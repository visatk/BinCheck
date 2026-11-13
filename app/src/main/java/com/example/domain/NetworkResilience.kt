package com.example.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Thread-safe bounded LRU cache with per-entry TTL expiration.
 * Works identically on Android runtime and pure JVM unit tests.
 */
class TtlLruCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private data class Entry<V>(
        val value: V,
        val expiresAtMillis: Long
    )

    private val map = object : LinkedHashMap<K, Entry<V>>(maxEntries.coerceAtLeast(16), 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean {
            return size > maxEntries
        }
    }

    private val lock = Any()

    fun get(key: K): V? = synchronized(lock) {
        val entry = map[key] ?: return null
        if (timeProvider() > entry.expiresAtMillis) {
            map.remove(key)
            return null
        }
        entry.value
    }

    fun put(key: K, value: V, ttlMillis: Long) = synchronized(lock) {
        if (ttlMillis <= 0L) {
            map.remove(key)
            return
        }
        map[key] = Entry(value = value, expiresAtMillis = timeProvider() + ttlMillis)
    }

    fun remove(key: K) = synchronized(lock) {
        map.remove(key)
        Unit
    }

    fun clear() = synchronized(lock) {
        map.clear()
    }

    fun size(): Int = synchronized(lock) {
        pruneExpiredLocked()
        map.size
    }

    private fun pruneExpiredLocked() {
        val now = timeProvider()
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            if (now > iterator.next().value.expiresAtMillis) {
                iterator.remove()
            }
        }
    }
}

/**
 * Single-flight in-flight request deduplicator.
 * If N concurrent callers request the same key, only 1 work lambda executes and
 * all callers await the exact same result; entries clean up automatically on completion.
 */
class RequestDeduplicator<K : Any, V> {
    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()

    suspend fun getOrExecute(key: K, block: suspend () -> V): Pair<V, Boolean> {
        while (true) {
            val existing = inFlight[key]
            if (existing != null) {
                return existing.await() to true
            }

            val created = CompletableDeferred<V>()
            val raced = inFlight.putIfAbsent(key, created)
            if (raced != null) {
                return raced.await() to true
            }

            return try {
                val result = block()
                created.complete(result)
                result to false
            } catch (t: Throwable) {
                created.completeExceptionally(t)
                throw t
            } finally {
                inFlight.remove(key, created)
            }
        }
    }

    fun activeInFlightCount(): Int = inFlight.size
}

/**
 * Per-host circuit breaker (Closed -> Open -> Half-Open) to avoid overloading struggling servers
 * and draining battery/data on guaranteed failures.
 */
class HostCircuitBreaker(
    private val failureThreshold: Int = 5,
    private val openCooldownMs: Long = 20_000L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private data class HostState(
        var consecutiveFailures: Int = 0,
        var openedAtMillis: Long = 0L,
        var state: State = State.CLOSED,
        var halfOpenProbeInFlight: Boolean = false
    )

    private val hosts = ConcurrentHashMap<String, HostState>()

    fun isRequestAllowed(host: String): Boolean {
        val key = host.lowercase()
        val hs = hosts[key] ?: return true
        synchronized(hs) {
            return when (hs.state) {
                State.CLOSED -> true
                State.OPEN -> {
                    val elapsed = timeProvider() - hs.openedAtMillis
                    if (elapsed >= openCooldownMs) {
                        hs.state = State.HALF_OPEN
                        hs.halfOpenProbeInFlight = true
                        true
                    } else {
                        false
                    }
                }
                State.HALF_OPEN -> {
                    if (!hs.halfOpenProbeInFlight) {
                        hs.halfOpenProbeInFlight = true
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    fun remainingCooldownMs(host: String): Long {
        val hs = hosts[host.lowercase()] ?: return 0L
        synchronized(hs) {
            if (hs.state != State.OPEN) return 0L
            return (openCooldownMs - (timeProvider() - hs.openedAtMillis)).coerceAtLeast(0L)
        }
    }

    fun recordSuccess(host: String) {
        val hs = hosts[host.lowercase()] ?: return
        synchronized(hs) {
            hs.consecutiveFailures = 0
            hs.openedAtMillis = 0L
            hs.state = State.CLOSED
            hs.halfOpenProbeInFlight = false
        }
    }

    /**
     * Releases an in-flight HALF_OPEN probe permit if the caller coroutine was cancelled
     * before completing, preventing permanent host lockout.
     */
    fun releaseHalfOpenProbe(host: String) {
        val hs = hosts[host.lowercase()] ?: return
        synchronized(hs) {
            if (hs.state == State.HALF_OPEN) {
                hs.halfOpenProbeInFlight = false
            }
        }
    }

    fun recordTransientFailure(host: String): Boolean {
        val hs = hosts.getOrPut(host.lowercase()) { HostState() }
        synchronized(hs) {
            hs.halfOpenProbeInFlight = false
            hs.consecutiveFailures++
            return if (hs.state == State.HALF_OPEN || hs.consecutiveFailures >= failureThreshold) {
                val newlyOpened = hs.state != State.OPEN
                hs.state = State.OPEN
                hs.openedAtMillis = timeProvider()
                newlyOpened
            } else {
                false
            }
        }
    }

    fun currentState(host: String): State {
        val hs = hosts[host.lowercase()] ?: return State.CLOSED
        synchronized(hs) {
            if (hs.state == State.OPEN && timeProvider() - hs.openedAtMillis >= openCooldownMs) {
                return State.HALF_OPEN
            }
            return hs.state
        }
    }

    fun resetAll() {
        hosts.clear()
    }
}

object RetryAndBackoffPolicy {

    val TRANSIENT_STATUS_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
    private const val MAX_RETRY_AFTER_MS = 30_000L

    fun computeBackoffDelayMs(
        attempt: Int,
        baseDelayMs: Long = 450L,
        maxDelayMs: Long = 8_000L,
        retryAfterMs: Long? = null,
        random: Random = Random.Default
    ): Long {
        if (retryAfterMs != null && retryAfterMs > 0L) {
            val jitter = random.nextLong(0L, 250L)
            return (retryAfterMs + jitter).coerceIn(baseDelayMs, MAX_RETRY_AFTER_MS)
        }
        val expFactor = 1L shl (attempt - 1).coerceIn(0, 6)
        val rawDelay = (baseDelayMs * expFactor).coerceAtMost(maxDelayMs)
        val jitterBound = (rawDelay / 3L).coerceAtLeast(1L)
        val jitter = random.nextLong(0L, jitterBound)
        return (rawDelay + jitter).coerceAtMost(maxDelayMs)
    }

    fun parseRetryAfterMs(
        retryAfterHeader: String?,
        responseBody: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        val header = retryAfterHeader?.trim().orEmpty()
        if (header.isNotEmpty()) {
            header.toLongOrNull()?.let { seconds ->
                return (seconds * 1000L).coerceIn(0L, MAX_RETRY_AFTER_MS)
            }
            val parsedDateMs = runCatching {
                val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
                fmt.parse(header)?.time
            }.getOrNull()
            if (parsedDateMs != null && parsedDateMs > nowMillis) {
                return (parsedDateMs - nowMillis).coerceIn(0L, MAX_RETRY_AFTER_MS)
            }
        }

        if (!responseBody.isNullOrBlank()) {
            val match = Regex(""""retry_after"\s*:\s*(\d+)""").find(responseBody)
            match?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { sec ->
                return (sec * 1000L).coerceIn(0L, MAX_RETRY_AFTER_MS)
            }
        }
        return null
    }

    suspend fun <T> executeWithExponentialBackoff(
        host: String,
        maxRetries: Int,
        baseDelayMs: Long = 450L,
        maxDelayMs: Long = 8_000L,
        circuitBreaker: HostCircuitBreaker? = null,
        telemetry: NetworkTelemetry? = null,
        callBlock: suspend () -> HttpCallRawResponse,
        onResult: (code: Int, body: String, attempts: Int) -> T,
        onCircuitOpen: (remainingMs: Long) -> T,
        onFailure: (reason: String, attempts: Int) -> T
    ): T {
        if (circuitBreaker != null && !circuitBreaker.isRequestAllowed(host)) {
            val waitMs = circuitBreaker.remainingCooldownMs(host)
            telemetry?.recordCircuitBlocked()
            return onCircuitOpen(waitMs)
        }

        val maxAttempts = 1 + maxRetries.coerceIn(0, 4)
        var lastError = "Network error"

        for (attempt in 1..maxAttempts) {
            currentCoroutineContext().ensureActive()
            try {
                telemetry?.recordHttpRequest()
                val res = callBlock()
                if (res.fromCache) {
                    telemetry?.recordCacheHit()
                }
                if (res.code in TRANSIENT_STATUS_CODES) {
                    lastError = "HTTP ${res.code}"
                    if (attempt < maxAttempts) {
                        telemetry?.recordRetry()
                        val waitMs = computeBackoffDelayMs(
                            attempt = attempt,
                            baseDelayMs = baseDelayMs,
                            maxDelayMs = maxDelayMs,
                            retryAfterMs = res.retryAfterMs
                        )
                        delay(waitMs)
                        continue
                    } else {
                        if (circuitBreaker?.recordTransientFailure(host) == true) {
                            telemetry?.recordCircuitTripped()
                        }
                        return onResult(res.code, res.body, attempt)
                    }
                }
                circuitBreaker?.recordSuccess(host)
                return onResult(res.code, res.body, attempt)
            } catch (e: IOException) {
                lastError = e.localizedMessage ?: e.javaClass.simpleName
                if (attempt < maxAttempts) {
                    telemetry?.recordRetry()
                    val waitMs = computeBackoffDelayMs(
                        attempt = attempt,
                        baseDelayMs = baseDelayMs,
                        maxDelayMs = maxDelayMs
                    )
                    delay(waitMs)
                } else {
                    if (circuitBreaker?.recordTransientFailure(host) == true) {
                        telemetry?.recordCircuitTripped()
                    }
                }
            } catch (ce: CancellationException) {
                circuitBreaker?.releaseHalfOpenProbe(host)
                throw ce
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                circuitBreaker?.releaseHalfOpenProbe(host)
                return onFailure(e.localizedMessage ?: e.javaClass.simpleName, attempt)
            }
        }
        return onFailure(lastError, maxAttempts)
    }
}

data class HttpCallRawResponse(
    val code: Int,
    val body: String,
    val retryAfterMs: Long? = null,
    val fromCache: Boolean = false
)

data class NetworkTelemetrySnapshot(
    val httpRequestsSent: Long = 0L,
    val cacheHits: Long = 0L,
    val deduplicatedSaved: Long = 0L,
    val retriesPerformed: Long = 0L,
    val circuitBreakerTrips: Int = 0,
    val circuitBlockedCalls: Long = 0L,
    val gzippedPayloadsSent: Long = 0L
) {
    val totalRequestsSaved: Long
        get() = cacheHits + deduplicatedSaved + circuitBlockedCalls
}

class NetworkTelemetry {
    private val httpRequests = AtomicLong(0L)
    private val cacheHits = AtomicLong(0L)
    private val dedupSaved = AtomicLong(0L)
    private val retries = AtomicLong(0L)
    private val circuitTrips = AtomicInteger(0)
    private val circuitBlocked = AtomicLong(0L)
    private val gzippedSent = AtomicLong(0L)

    fun recordHttpRequest() {
        httpRequests.incrementAndGet()
    }

    fun recordCacheHit() {
        cacheHits.incrementAndGet()
    }

    fun recordDeduplicatedSaved() {
        dedupSaved.incrementAndGet()
    }

    fun recordRetry() {
        retries.incrementAndGet()
    }

    fun recordCircuitTripped() {
        circuitTrips.incrementAndGet()
    }

    fun recordCircuitBlocked() {
        circuitBlocked.incrementAndGet()
    }

    fun recordGzippedPayload() {
        gzippedSent.incrementAndGet()
    }

    fun snapshot(): NetworkTelemetrySnapshot = NetworkTelemetrySnapshot(
        httpRequestsSent = httpRequests.get(),
        cacheHits = cacheHits.get(),
        deduplicatedSaved = dedupSaved.get(),
        retriesPerformed = retries.get(),
        circuitBreakerTrips = circuitTrips.get(),
        circuitBlockedCalls = circuitBlocked.get(),
        gzippedPayloadsSent = gzippedSent.get()
    )

    fun reset() {
        httpRequests.set(0L)
        cacheHits.set(0L)
        dedupSaved.set(0L)
        retries.set(0L)
        circuitTrips.set(0)
        circuitBlocked.set(0L)
        gzippedSent.set(0L)
    }
}
