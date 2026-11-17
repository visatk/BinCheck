package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.CustomConfigProfile
import com.example.domain.CustomConfigStudioEngine
import com.example.domain.CustomHttpConfig
import com.example.domain.HttpRequestMethod
import com.example.domain.ProxyProtocol
import com.example.domain.ProxyRotationMode
import com.example.domain.TelegramWebhookConfig
import com.example.domain.ValidationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "validator_settings")

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

data class ValidatorSettings(
    val validationMode: ValidationMode = ValidationMode.LOCAL_DETERMINISTIC,
    val webhookUrl: String = "",
    val httpMethod: HttpRequestMethod = HttpRequestMethod.AUTO,
    val httpHeaders: String = "",
    val httpBodyTemplate: String = "",
    val httpTimeoutSeconds: Int = 12,
    val httpMaxRetries: Int = 1,
    val httpLiveKeywords: String = CustomHttpConfig.DEFAULT_LIVE_KEYWORDS,
    val httpDieKeywords: String = CustomHttpConfig.DEFAULT_DIE_KEYWORDS,
    val httpProfileName: String = CustomHttpConfig.DEFAULT_PROFILE_NAME,
    val httpUserAgent: String = CustomHttpConfig.DEFAULT_USER_AGENT,
    val httpFollowRedirects: Boolean = true,
    val httpParseRulesSpec: String = "",
    val httpCheckpointRulesSpec: String = "",
    val httpProxyEnabled: Boolean = false,
    val httpProxyProtocol: ProxyProtocol = ProxyProtocol.HTTP,
    val httpProxyRotation: ProxyRotationMode = ProxyRotationMode.ROUND_ROBIN,
    val httpProxyListText: String = "",
    val httpPreRequestEnabled: Boolean = false,
    val httpPreRequestUrl: String = "",
    val httpPreRequestMethod: HttpRequestMethod = HttpRequestMethod.POST_FORM,
    val httpPreRequestHeaders: String = "",
    val httpPreRequestBody: String = "",
    val httpPreRequestParseRulesSpec: String = "",
    val httpSkipLocalLuhnPrecheck: Boolean = false,
    val httpBlocksSpec: String = "",
    val savedConfigProfilesRaw: String = "",
    val autoResolveWildcards: Boolean = true,
    val autoBinLookupForLive: Boolean = true,
    val batchDelayMs: Long = 55L,
    val concurrency: Int = 4,
    val patternTemplate: String = "453225005536xxx3|08|2028|113",
    val generateQuantity: Int = 10,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val telegramWebhookEnabled: Boolean = false,
    val telegramChatId: String = "",
    val telegramNotifyLiveOnly: Boolean = true,
    val deduplicateBatchInputs: Boolean = true,
    val gzipCustomRequests: Boolean = false,
    val wifiOnlyBatch: Boolean = false,
    val batterySaverMode: Boolean = false,
    val hasPromptedNotifications: Boolean = false,
    val firebaseMetricsEnabled: Boolean = true
) {
    val effectiveDelayMs: Long
        get() = validationMode.resolveDelayMs(batchDelayMs)

    fun toCustomHttpConfig(): CustomHttpConfig = CustomHttpConfig(
        url = webhookUrl,
        method = httpMethod,
        headersText = httpHeaders,
        bodyTemplate = httpBodyTemplate,
        timeoutSeconds = httpTimeoutSeconds,
        maxRetries = httpMaxRetries,
        liveKeywords = httpLiveKeywords,
        dieKeywords = httpDieKeywords,
        compressBodyGzip = gzipCustomRequests,
        profileName = httpProfileName,
        userAgent = httpUserAgent,
        followRedirects = httpFollowRedirects,
        parseRulesSpec = httpParseRulesSpec,
        checkpointRulesSpec = httpCheckpointRulesSpec,
        proxyEnabled = httpProxyEnabled,
        proxyProtocol = httpProxyProtocol,
        proxyRotation = httpProxyRotation,
        proxyListText = httpProxyListText,
        preRequestEnabled = httpPreRequestEnabled,
        preRequestUrl = httpPreRequestUrl,
        preRequestMethod = httpPreRequestMethod,
        preRequestHeaders = httpPreRequestHeaders,
        preRequestBody = httpPreRequestBody,
        preRequestParseRulesSpec = httpPreRequestParseRulesSpec,
        skipLocalLuhnPrecheck = httpSkipLocalLuhnPrecheck,
        blocksSpec = httpBlocksSpec
    )

    fun savedCustomProfiles(): List<CustomConfigProfile> =
        CustomConfigStudioEngine.parseSavedProfiles(savedConfigProfilesRaw)

    fun toTelegramWebhookConfig(): TelegramWebhookConfig = TelegramWebhookConfig(
        enabled = telegramWebhookEnabled,
        chatId = telegramChatId,
        notifyLiveOnly = telegramNotifyLiveOnly
    )
}

class ValidatorPreferencesRepository(private val context: Context) {

    private object Keys {
        val VALIDATION_MODE = stringPreferencesKey("validation_mode")
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val HTTP_METHOD = stringPreferencesKey("http_method")
        val HTTP_HEADERS = stringPreferencesKey("http_headers")
        val HTTP_BODY_TEMPLATE = stringPreferencesKey("http_body_template")
        val HTTP_TIMEOUT_SECONDS = intPreferencesKey("http_timeout_seconds")
        val HTTP_MAX_RETRIES = intPreferencesKey("http_max_retries")
        val HTTP_LIVE_KEYWORDS = stringPreferencesKey("http_live_keywords")
        val HTTP_DIE_KEYWORDS = stringPreferencesKey("http_die_keywords")
        val HTTP_PROFILE_NAME = stringPreferencesKey("http_profile_name")
        val HTTP_USER_AGENT = stringPreferencesKey("http_user_agent")
        val HTTP_FOLLOW_REDIRECTS = booleanPreferencesKey("http_follow_redirects")
        val HTTP_PARSE_RULES_SPEC = stringPreferencesKey("http_parse_rules_spec")
        val HTTP_CHECKPOINT_RULES_SPEC = stringPreferencesKey("http_checkpoint_rules_spec")
        val HTTP_PROXY_ENABLED = booleanPreferencesKey("http_proxy_enabled")
        val HTTP_PROXY_PROTOCOL = stringPreferencesKey("http_proxy_protocol")
        val HTTP_PROXY_ROTATION = stringPreferencesKey("http_proxy_rotation")
        val HTTP_PROXY_LIST_TEXT = stringPreferencesKey("http_proxy_list_text")
        val HTTP_PRE_REQUEST_ENABLED = booleanPreferencesKey("http_pre_request_enabled")
        val HTTP_PRE_REQUEST_URL = stringPreferencesKey("http_pre_request_url")
        val HTTP_PRE_REQUEST_METHOD = stringPreferencesKey("http_pre_request_method")
        val HTTP_PRE_REQUEST_HEADERS = stringPreferencesKey("http_pre_request_headers")
        val HTTP_PRE_REQUEST_BODY = stringPreferencesKey("http_pre_request_body")
        val HTTP_PRE_REQUEST_PARSE_RULES_SPEC = stringPreferencesKey("http_pre_request_parse_rules_spec")
        val HTTP_SKIP_LOCAL_LUHN_PRECHECK = booleanPreferencesKey("http_skip_local_luhn_precheck")
        val HTTP_BLOCKS_SPEC = stringPreferencesKey("http_blocks_spec")
        val SAVED_CONFIG_PROFILES_RAW = stringPreferencesKey("saved_config_profiles_raw")
        val AUTO_RESOLVE_WILDCARDS = booleanPreferencesKey("auto_resolve_wildcards")
        val AUTO_BIN_LOOKUP_FOR_LIVE = booleanPreferencesKey("auto_bin_lookup_for_live")
        val BATCH_DELAY_MS = longPreferencesKey("batch_delay_ms")
        val CONCURRENCY = intPreferencesKey("worker_concurrency")
        val PATTERN_TEMPLATE = stringPreferencesKey("pattern_template")
        val GENERATE_QUANTITY = intPreferencesKey("generate_quantity")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val TELEGRAM_WEBHOOK_ENABLED = booleanPreferencesKey("telegram_webhook_enabled")
        val TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val TELEGRAM_NOTIFY_LIVE_ONLY = booleanPreferencesKey("telegram_notify_live_only")
        val DEDUPLICATE_BATCH_INPUTS = booleanPreferencesKey("deduplicate_batch_inputs")
        val GZIP_CUSTOM_REQUESTS = booleanPreferencesKey("gzip_custom_requests")
        val WIFI_ONLY_BATCH = booleanPreferencesKey("wifi_only_batch")
        val BATTERY_SAVER_MODE = booleanPreferencesKey("battery_saver_mode")
        val HAS_PROMPTED_NOTIFICATIONS = booleanPreferencesKey("has_prompted_notifications")
        val FIREBASE_METRICS_ENABLED = booleanPreferencesKey("firebase_metrics_enabled")
    }

    val settingsFlow: Flow<ValidatorSettings> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[Keys.VALIDATION_MODE] ?: ValidationMode.LOCAL_DETERMINISTIC.name
        val mode = runCatching { ValidationMode.valueOf(modeStr) }
            .getOrDefault(ValidationMode.LOCAL_DETERMINISTIC)

        val methodStr = prefs[Keys.HTTP_METHOD] ?: HttpRequestMethod.AUTO.name
        val httpMethod = runCatching { HttpRequestMethod.valueOf(methodStr) }
            .getOrDefault(HttpRequestMethod.AUTO)

        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val themeMode = runCatching { AppThemeMode.valueOf(themeStr) }
            .getOrDefault(AppThemeMode.SYSTEM)

        val proxyProtoStr = prefs[Keys.HTTP_PROXY_PROTOCOL] ?: ProxyProtocol.HTTP.name
        val proxyProtocol = runCatching { ProxyProtocol.valueOf(proxyProtoStr) }
            .getOrDefault(ProxyProtocol.HTTP)

        val proxyRotStr = prefs[Keys.HTTP_PROXY_ROTATION] ?: ProxyRotationMode.ROUND_ROBIN.name
        val proxyRotation = runCatching { ProxyRotationMode.valueOf(proxyRotStr) }
            .getOrDefault(ProxyRotationMode.ROUND_ROBIN)

        val preMethodStr = prefs[Keys.HTTP_PRE_REQUEST_METHOD] ?: HttpRequestMethod.POST_FORM.name
        val preRequestMethod = runCatching { HttpRequestMethod.valueOf(preMethodStr) }
            .getOrDefault(HttpRequestMethod.POST_FORM)

        ValidatorSettings(
            validationMode = mode,
            webhookUrl = prefs[Keys.WEBHOOK_URL] ?: "",
            httpMethod = httpMethod,
            httpHeaders = prefs[Keys.HTTP_HEADERS] ?: "",
            httpBodyTemplate = prefs[Keys.HTTP_BODY_TEMPLATE] ?: "",
            httpTimeoutSeconds = (prefs[Keys.HTTP_TIMEOUT_SECONDS] ?: 12).coerceIn(5, 60),
            httpMaxRetries = (prefs[Keys.HTTP_MAX_RETRIES] ?: 1).coerceIn(0, 3),
            httpLiveKeywords = prefs[Keys.HTTP_LIVE_KEYWORDS] ?: CustomHttpConfig.DEFAULT_LIVE_KEYWORDS,
            httpDieKeywords = prefs[Keys.HTTP_DIE_KEYWORDS] ?: CustomHttpConfig.DEFAULT_DIE_KEYWORDS,
            httpProfileName = prefs[Keys.HTTP_PROFILE_NAME] ?: CustomHttpConfig.DEFAULT_PROFILE_NAME,
            httpUserAgent = prefs[Keys.HTTP_USER_AGENT] ?: CustomHttpConfig.DEFAULT_USER_AGENT,
            httpFollowRedirects = prefs[Keys.HTTP_FOLLOW_REDIRECTS] ?: true,
            httpParseRulesSpec = prefs[Keys.HTTP_PARSE_RULES_SPEC] ?: "",
            httpCheckpointRulesSpec = prefs[Keys.HTTP_CHECKPOINT_RULES_SPEC] ?: "",
            httpProxyEnabled = prefs[Keys.HTTP_PROXY_ENABLED] ?: false,
            httpProxyProtocol = proxyProtocol,
            httpProxyRotation = proxyRotation,
            httpProxyListText = prefs[Keys.HTTP_PROXY_LIST_TEXT] ?: "",
            httpPreRequestEnabled = prefs[Keys.HTTP_PRE_REQUEST_ENABLED] ?: false,
            httpPreRequestUrl = prefs[Keys.HTTP_PRE_REQUEST_URL] ?: "",
            httpPreRequestMethod = preRequestMethod,
            httpPreRequestHeaders = prefs[Keys.HTTP_PRE_REQUEST_HEADERS] ?: "",
            httpPreRequestBody = prefs[Keys.HTTP_PRE_REQUEST_BODY] ?: "",
            httpPreRequestParseRulesSpec = prefs[Keys.HTTP_PRE_REQUEST_PARSE_RULES_SPEC] ?: "",
            httpSkipLocalLuhnPrecheck = prefs[Keys.HTTP_SKIP_LOCAL_LUHN_PRECHECK] ?: false,
            httpBlocksSpec = prefs[Keys.HTTP_BLOCKS_SPEC] ?: "",
            savedConfigProfilesRaw = prefs[Keys.SAVED_CONFIG_PROFILES_RAW] ?: "",
            autoResolveWildcards = prefs[Keys.AUTO_RESOLVE_WILDCARDS] ?: true,
            autoBinLookupForLive = prefs[Keys.AUTO_BIN_LOOKUP_FOR_LIVE] ?: true,
            batchDelayMs = (prefs[Keys.BATCH_DELAY_MS] ?: 55L).coerceIn(0L, ValidationMode.MAX_CUSTOM_DELAY_MS),
            concurrency = (prefs[Keys.CONCURRENCY] ?: 4).coerceIn(1, 8),
            patternTemplate = prefs[Keys.PATTERN_TEMPLATE] ?: "453225005536xxx3|08|2028|113",
            generateQuantity = prefs[Keys.GENERATE_QUANTITY] ?: 10,
            themeMode = themeMode,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
            telegramWebhookEnabled = prefs[Keys.TELEGRAM_WEBHOOK_ENABLED] ?: false,
            telegramChatId = prefs[Keys.TELEGRAM_CHAT_ID] ?: "",
            telegramNotifyLiveOnly = prefs[Keys.TELEGRAM_NOTIFY_LIVE_ONLY] ?: true,
            deduplicateBatchInputs = prefs[Keys.DEDUPLICATE_BATCH_INPUTS] ?: true,
            gzipCustomRequests = prefs[Keys.GZIP_CUSTOM_REQUESTS] ?: false,
            wifiOnlyBatch = prefs[Keys.WIFI_ONLY_BATCH] ?: false,
            batterySaverMode = prefs[Keys.BATTERY_SAVER_MODE] ?: false,
            hasPromptedNotifications = prefs[Keys.HAS_PROMPTED_NOTIFICATIONS] ?: false,
            firebaseMetricsEnabled = prefs[Keys.FIREBASE_METRICS_ENABLED] ?: true
        )
    }

    suspend fun setValidationMode(mode: ValidationMode) {
        context.dataStore.edit { it[Keys.VALIDATION_MODE] = mode.name }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHasPromptedNotifications(prompted: Boolean) {
        context.dataStore.edit { it[Keys.HAS_PROMPTED_NOTIFICATIONS] = prompted }
    }

    suspend fun setFirebaseMetricsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FIREBASE_METRICS_ENABLED] = enabled }
    }

    suspend fun applyCustomHttpConfig(
        config: CustomHttpConfig,
        activateRemoteWebhookMode: Boolean = true
    ) {
        context.dataStore.edit { prefs ->
            if (activateRemoteWebhookMode) {
                prefs[Keys.VALIDATION_MODE] = ValidationMode.REMOTE_WEBHOOK.name
            }
            prefs[Keys.WEBHOOK_URL] = config.url.trim()
            prefs[Keys.HTTP_METHOD] = config.method.name
            prefs[Keys.HTTP_HEADERS] = config.headersText.trim()
            prefs[Keys.HTTP_BODY_TEMPLATE] = config.bodyTemplate.trim()
            prefs[Keys.HTTP_TIMEOUT_SECONDS] = config.timeoutSeconds.coerceIn(5, 60)
            prefs[Keys.HTTP_MAX_RETRIES] = config.maxRetries.coerceIn(0, 3)
            prefs[Keys.HTTP_LIVE_KEYWORDS] = config.liveKeywords.trim().ifEmpty { CustomHttpConfig.DEFAULT_LIVE_KEYWORDS }
            prefs[Keys.HTTP_DIE_KEYWORDS] = config.dieKeywords.trim().ifEmpty { CustomHttpConfig.DEFAULT_DIE_KEYWORDS }
            prefs[Keys.GZIP_CUSTOM_REQUESTS] = config.compressBodyGzip
            prefs[Keys.HTTP_PROFILE_NAME] = config.profileName.trim().ifEmpty { CustomHttpConfig.DEFAULT_PROFILE_NAME }
            prefs[Keys.HTTP_USER_AGENT] = config.userAgent.trim()
            prefs[Keys.HTTP_FOLLOW_REDIRECTS] = config.followRedirects
            prefs[Keys.HTTP_PARSE_RULES_SPEC] = config.parseRulesSpec
            prefs[Keys.HTTP_CHECKPOINT_RULES_SPEC] = config.checkpointRulesSpec
            prefs[Keys.HTTP_PROXY_ENABLED] = config.proxyEnabled
            prefs[Keys.HTTP_PROXY_PROTOCOL] = config.proxyProtocol.name
            prefs[Keys.HTTP_PROXY_ROTATION] = config.proxyRotation.name
            prefs[Keys.HTTP_PROXY_LIST_TEXT] = config.proxyListText.trim()
            prefs[Keys.HTTP_PRE_REQUEST_ENABLED] = config.preRequestEnabled
            prefs[Keys.HTTP_PRE_REQUEST_URL] = config.preRequestUrl.trim()
            prefs[Keys.HTTP_PRE_REQUEST_METHOD] = config.preRequestMethod.name
            prefs[Keys.HTTP_PRE_REQUEST_HEADERS] = config.preRequestHeaders.trim()
            prefs[Keys.HTTP_PRE_REQUEST_BODY] = config.preRequestBody.trim()
            prefs[Keys.HTTP_PRE_REQUEST_PARSE_RULES_SPEC] = config.preRequestParseRulesSpec
            prefs[Keys.HTTP_SKIP_LOCAL_LUHN_PRECHECK] = config.skipLocalLuhnPrecheck
            prefs[Keys.HTTP_BLOCKS_SPEC] = config.blocksSpec
        }
    }

    suspend fun saveCustomProfile(profile: CustomConfigProfile) {
        context.dataStore.edit { prefs ->
            val existing = CustomConfigStudioEngine.parseSavedProfiles(prefs[Keys.SAVED_CONFIG_PROFILES_RAW].orEmpty())
                .toMutableList()
            val index = existing.indexOfFirst { it.id == profile.id || it.name.equals(profile.name, ignoreCase = true) }
            if (index >= 0) {
                existing[index] = profile.copy(isBuiltIn = false)
            } else {
                existing.add(0, profile.copy(isBuiltIn = false))
            }
            prefs[Keys.SAVED_CONFIG_PROFILES_RAW] = CustomConfigStudioEngine.serializeSavedProfiles(existing.take(25))
        }
    }

    suspend fun deleteCustomProfile(profileId: String) {
        context.dataStore.edit { prefs ->
            val filtered = CustomConfigStudioEngine.parseSavedProfiles(prefs[Keys.SAVED_CONFIG_PROFILES_RAW].orEmpty())
                .filterNot { it.id == profileId }
            prefs[Keys.SAVED_CONFIG_PROFILES_RAW] = CustomConfigStudioEngine.serializeSavedProfiles(filtered)
        }
    }

    suspend fun updateSettings(
        validationMode: ValidationMode,
        webhookUrl: String,
        httpMethod: HttpRequestMethod = HttpRequestMethod.AUTO,
        httpHeaders: String = "",
        httpBodyTemplate: String = "",
        httpTimeoutSeconds: Int = 12,
        httpMaxRetries: Int = 1,
        httpLiveKeywords: String = CustomHttpConfig.DEFAULT_LIVE_KEYWORDS,
        httpDieKeywords: String = CustomHttpConfig.DEFAULT_DIE_KEYWORDS,
        httpProfileName: String? = null,
        httpUserAgent: String? = null,
        httpFollowRedirects: Boolean? = null,
        httpParseRulesSpec: String? = null,
        httpCheckpointRulesSpec: String? = null,
        httpProxyEnabled: Boolean? = null,
        httpProxyProtocol: ProxyProtocol? = null,
        httpProxyRotation: ProxyRotationMode? = null,
        httpProxyListText: String? = null,
        httpPreRequestEnabled: Boolean? = null,
        httpPreRequestUrl: String? = null,
        httpPreRequestMethod: HttpRequestMethod? = null,
        httpPreRequestHeaders: String? = null,
        httpPreRequestBody: String? = null,
        httpPreRequestParseRulesSpec: String? = null,
        httpSkipLocalLuhnPrecheck: Boolean? = null,
        httpBlocksSpec: String? = null,
        autoResolveWildcards: Boolean,
        autoBinLookupForLive: Boolean = true,
        batchDelayMs: Long,
        concurrency: Int,
        patternTemplate: String,
        generateQuantity: Int,
        themeMode: AppThemeMode,
        useDynamicColor: Boolean,
        telegramWebhookEnabled: Boolean = false,
        telegramChatId: String = "",
        telegramNotifyLiveOnly: Boolean = true,
        deduplicateBatchInputs: Boolean = true,
        gzipCustomRequests: Boolean = false,
        wifiOnlyBatch: Boolean = false,
        batterySaverMode: Boolean = false,
        firebaseMetricsEnabled: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VALIDATION_MODE] = validationMode.name
            prefs[Keys.WEBHOOK_URL] = webhookUrl.trim()
            prefs[Keys.HTTP_METHOD] = httpMethod.name
            prefs[Keys.HTTP_HEADERS] = httpHeaders.trim()
            prefs[Keys.HTTP_BODY_TEMPLATE] = httpBodyTemplate.trim()
            prefs[Keys.HTTP_TIMEOUT_SECONDS] = httpTimeoutSeconds.coerceIn(5, 60)
            prefs[Keys.HTTP_MAX_RETRIES] = httpMaxRetries.coerceIn(0, 3)
            prefs[Keys.HTTP_LIVE_KEYWORDS] = httpLiveKeywords.trim().ifEmpty { CustomHttpConfig.DEFAULT_LIVE_KEYWORDS }
            prefs[Keys.HTTP_DIE_KEYWORDS] = httpDieKeywords.trim().ifEmpty { CustomHttpConfig.DEFAULT_DIE_KEYWORDS }
            httpProfileName?.let { prefs[Keys.HTTP_PROFILE_NAME] = it.trim().ifEmpty { CustomHttpConfig.DEFAULT_PROFILE_NAME } }
            httpUserAgent?.let { prefs[Keys.HTTP_USER_AGENT] = it.trim() }
            httpFollowRedirects?.let { prefs[Keys.HTTP_FOLLOW_REDIRECTS] = it }
            httpParseRulesSpec?.let { prefs[Keys.HTTP_PARSE_RULES_SPEC] = it }
            httpCheckpointRulesSpec?.let { prefs[Keys.HTTP_CHECKPOINT_RULES_SPEC] = it }
            httpProxyEnabled?.let { prefs[Keys.HTTP_PROXY_ENABLED] = it }
            httpProxyProtocol?.let { prefs[Keys.HTTP_PROXY_PROTOCOL] = it.name }
            httpProxyRotation?.let { prefs[Keys.HTTP_PROXY_ROTATION] = it.name }
            httpProxyListText?.let { prefs[Keys.HTTP_PROXY_LIST_TEXT] = it.trim() }
            httpPreRequestEnabled?.let { prefs[Keys.HTTP_PRE_REQUEST_ENABLED] = it }
            httpPreRequestUrl?.let { prefs[Keys.HTTP_PRE_REQUEST_URL] = it.trim() }
            httpPreRequestMethod?.let { prefs[Keys.HTTP_PRE_REQUEST_METHOD] = it.name }
            httpPreRequestHeaders?.let { prefs[Keys.HTTP_PRE_REQUEST_HEADERS] = it.trim() }
            httpPreRequestBody?.let { prefs[Keys.HTTP_PRE_REQUEST_BODY] = it.trim() }
            httpPreRequestParseRulesSpec?.let { prefs[Keys.HTTP_PRE_REQUEST_PARSE_RULES_SPEC] = it }
            httpSkipLocalLuhnPrecheck?.let { prefs[Keys.HTTP_SKIP_LOCAL_LUHN_PRECHECK] = it }
            httpBlocksSpec?.let { prefs[Keys.HTTP_BLOCKS_SPEC] = it }
            prefs[Keys.AUTO_RESOLVE_WILDCARDS] = autoResolveWildcards
            prefs[Keys.AUTO_BIN_LOOKUP_FOR_LIVE] = autoBinLookupForLive
            prefs[Keys.BATCH_DELAY_MS] = batchDelayMs.coerceIn(0L, ValidationMode.MAX_CUSTOM_DELAY_MS)
            prefs[Keys.CONCURRENCY] = concurrency.coerceIn(1, 8)
            prefs[Keys.PATTERN_TEMPLATE] = patternTemplate.trim().ifEmpty { "453225005536xxx3|08|2028|113" }
            prefs[Keys.GENERATE_QUANTITY] = generateQuantity.coerceIn(1, 100)
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
            prefs[Keys.TELEGRAM_WEBHOOK_ENABLED] = telegramWebhookEnabled
            prefs[Keys.TELEGRAM_CHAT_ID] = telegramChatId.trim()
            prefs[Keys.TELEGRAM_NOTIFY_LIVE_ONLY] = telegramNotifyLiveOnly
            prefs[Keys.DEDUPLICATE_BATCH_INPUTS] = deduplicateBatchInputs
            prefs[Keys.GZIP_CUSTOM_REQUESTS] = gzipCustomRequests
            prefs[Keys.WIFI_ONLY_BATCH] = wifiOnlyBatch
            prefs[Keys.BATTERY_SAVER_MODE] = batterySaverMode
            firebaseMetricsEnabled?.let { prefs[Keys.FIREBASE_METRICS_ENABLED] = it }
        }
    }
}
