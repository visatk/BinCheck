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
import com.example.domain.CustomHttpConfig
import com.example.domain.HttpRequestMethod
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
    val telegramNotifyLiveOnly: Boolean = true
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
        dieKeywords = httpDieKeywords
    )

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
            telegramNotifyLiveOnly = prefs[Keys.TELEGRAM_NOTIFY_LIVE_ONLY] ?: true
        )
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
        telegramNotifyLiveOnly: Boolean = true
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
        }
    }
}
