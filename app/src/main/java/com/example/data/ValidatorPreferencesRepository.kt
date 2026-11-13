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
    val autoResolveWildcards: Boolean = true,
    val batchDelayMs: Long = 55L,
    val concurrency: Int = 4,
    val patternTemplate: String = "453225005536xxx3|08|2028|113",
    val generateQuantity: Int = 10,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true
)

class ValidatorPreferencesRepository(private val context: Context) {

    private object Keys {
        val VALIDATION_MODE = stringPreferencesKey("validation_mode")
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val AUTO_RESOLVE_WILDCARDS = booleanPreferencesKey("auto_resolve_wildcards")
        val BATCH_DELAY_MS = longPreferencesKey("batch_delay_ms")
        val CONCURRENCY = intPreferencesKey("worker_concurrency")
        val PATTERN_TEMPLATE = stringPreferencesKey("pattern_template")
        val GENERATE_QUANTITY = intPreferencesKey("generate_quantity")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }

    val settingsFlow: Flow<ValidatorSettings> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[Keys.VALIDATION_MODE] ?: ValidationMode.LOCAL_DETERMINISTIC.name
        val mode = runCatching { ValidationMode.valueOf(modeStr) }
            .getOrDefault(ValidationMode.LOCAL_DETERMINISTIC)

        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val themeMode = runCatching { AppThemeMode.valueOf(themeStr) }
            .getOrDefault(AppThemeMode.SYSTEM)

        ValidatorSettings(
            validationMode = mode,
            webhookUrl = prefs[Keys.WEBHOOK_URL] ?: "",
            autoResolveWildcards = prefs[Keys.AUTO_RESOLVE_WILDCARDS] ?: true,
            batchDelayMs = prefs[Keys.BATCH_DELAY_MS] ?: 55L,
            concurrency = (prefs[Keys.CONCURRENCY] ?: 4).coerceIn(1, 8),
            patternTemplate = prefs[Keys.PATTERN_TEMPLATE] ?: "453225005536xxx3|08|2028|113",
            generateQuantity = prefs[Keys.GENERATE_QUANTITY] ?: 10,
            themeMode = themeMode,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true
        )
    }

    suspend fun updateSettings(
        validationMode: ValidationMode,
        webhookUrl: String,
        autoResolveWildcards: Boolean,
        batchDelayMs: Long,
        concurrency: Int,
        patternTemplate: String,
        generateQuantity: Int,
        themeMode: AppThemeMode,
        useDynamicColor: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VALIDATION_MODE] = validationMode.name
            prefs[Keys.WEBHOOK_URL] = webhookUrl.trim()
            prefs[Keys.AUTO_RESOLVE_WILDCARDS] = autoResolveWildcards
            prefs[Keys.BATCH_DELAY_MS] = batchDelayMs.coerceIn(0L, 2000L)
            prefs[Keys.CONCURRENCY] = concurrency.coerceIn(1, 8)
            prefs[Keys.PATTERN_TEMPLATE] = patternTemplate.trim().ifEmpty { "453225005536xxx3|08|2028|113" }
            prefs[Keys.GENERATE_QUANTITY] = generateQuantity.coerceIn(1, 100)
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }
}
