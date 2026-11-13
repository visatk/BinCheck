package com.example.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DeviceNetworkStatus(
    val isConnected: Boolean = true,
    val isMetered: Boolean = false,
    val isBackgroundDataRestricted: Boolean = false,
    val isPowerSaveMode: Boolean = false
) {
    val shouldThrottleForBatteryOrData: Boolean
        get() = isBackgroundDataRestricted || isPowerSaveMode
}

/**
 * Centralized shared OkHttpClient, disk HTTP Cache, ConnectionPool, Dispatcher,
 * CircuitBreaker, GZIP compression utilities, and global telemetry.
 */
object NetworkClientProvider {

    private const val HTTP_CACHE_SIZE_BYTES = 10L * 1024L * 1024L // 10 MiB
    private const val MAX_RESPONSE_BODY_BYTES = 1024L * 1024L // 1 MiB safety cap

    val telemetry = NetworkTelemetry()
    val circuitBreaker = HostCircuitBreaker(
        failureThreshold = 5,
        openCooldownMs = 20_000L
    )

    @Volatile
    private var appCacheDir: File? = null

    @Volatile
    private var cachedOkHttpClient: OkHttpClient? = null
    private val scopedClientsByTimeoutSec = ConcurrentHashMap<Int, OkHttpClient>()

    fun init(context: Context) {
        val targetDir = File(context.applicationContext.cacheDir, "bincheck_http_cache")
        if (appCacheDir == null) {
            synchronized(this) {
                if (appCacheDir == null) {
                    appCacheDir = targetDir
                    cachedOkHttpClient = null // rebuild once with disk Cache attached
                    scopedClientsByTimeoutSec.clear()
                }
            }
        }
    }

    val baseClient: OkHttpClient
        get() = cachedOkHttpClient ?: synchronized(this) {
            cachedOkHttpClient ?: buildBaseClient().also { cachedOkHttpClient = it }
        }

    private fun buildBaseClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 12
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)

        appCacheDir?.let { dir ->
            runCatching {
                if (!dir.exists()) dir.mkdirs()
                builder.cache(Cache(dir, HTTP_CACHE_SIZE_BYTES))
            }
        }
        return builder.build()
    }

    /**
     * Derives a cached, scoped OkHttpClient sharing the global ConnectionPool, Dispatcher, and Cache.
     */
    fun clientWithTimeout(timeoutSeconds: Int): OkHttpClient {
        val clamped = timeoutSeconds.coerceIn(5, 60)
        return scopedClientsByTimeoutSec.getOrPut(clamped) {
            val sec = clamped.toLong()
            baseClient.newBuilder()
                .connectTimeout(sec, TimeUnit.SECONDS)
                .readTimeout(sec, TimeUnit.SECONDS)
                .writeTimeout(sec, TimeUnit.SECONDS)
                .callTimeout(sec + 5L, TimeUnit.SECONDS)
                .build()
        }
    }

    fun clearDiskHttpCache() {
        runCatching { baseClient.cache?.evictAll() }
    }

    fun diskCacheSizeBytes(): Long {
        return runCatching { baseClient.cache?.size() ?: 0L }.getOrDefault(0L)
    }

    /**
     * Creates a streaming GZIP-compressed RequestBody for custom endpoints that support
     * Content-Encoding: gzip.
     */
    fun createGzipRequestBody(
        plainText: String,
        contentType: MediaType
    ): RequestBody {
        val rawBytes = plainText.toByteArray(Charsets.UTF_8)
        telemetry.recordGzippedPayload()
        return object : RequestBody() {
            override fun contentType(): MediaType = contentType
            override fun contentLength(): Long = -1L
            override fun writeTo(sink: BufferedSink) {
                GzipSink(sink).buffer().use { gzipSink ->
                    gzipSink.write(rawBytes)
                }
            }
        }
    }

    fun compressUtf8ToGzip(plainText: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(plainText.toByteArray(Charsets.UTF_8))
        }
        return out.toByteArray()
    }

    fun inspectDeviceNetworkStatus(context: Context): DeviceNetworkStatus {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val isPowerSave = pm?.isPowerSaveMode == true
        if (cm == null) {
            return DeviceNetworkStatus(isPowerSaveMode = isPowerSave)
        }

        val activeNetwork = cm.activeNetwork
        val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val metered = cm.isActiveNetworkMetered
        val backgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } else {
            false
        }

        return DeviceNetworkStatus(
            isConnected = connected,
            isMetered = metered,
            isBackgroundDataRestricted = backgroundRestricted,
            isPowerSaveMode = isPowerSave
        )
    }

    suspend fun awaitRawCall(
        client: OkHttpClient,
        request: Request
    ): HttpCallRawResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (continuation.isActive) {
                        try {
                            val code = res.code
                            val body = res.body?.let { responseBody ->
                                val source = responseBody.source()
                                source.request(MAX_RESPONSE_BODY_BYTES)
                                val bytesToRead = minOf(source.buffer.size, MAX_RESPONSE_BODY_BYTES)
                                source.readUtf8(bytesToRead)
                            }.orEmpty()
                            val retryAfter = RetryAndBackoffPolicy.parseRetryAfterMs(
                                retryAfterHeader = res.header("Retry-After"),
                                responseBody = body
                            )
                            val fromCache = res.networkResponse == null && res.cacheResponse != null
                            continuation.resume(
                                HttpCallRawResponse(
                                    code = code,
                                    body = body,
                                    retryAfterMs = retryAfter,
                                    fromCache = fromCache
                                )
                            )
                        } catch (t: Throwable) {
                            continuation.resumeWithException(t)
                        }
                    }
                }
            }
        })
    }
}
