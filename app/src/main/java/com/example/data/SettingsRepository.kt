package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "screenai_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MODEL_ID = stringPreferencesKey("model_id")
        val RESPONSE_LENGTH = stringPreferencesKey("response_length")
        val THEME_PREF = stringPreferencesKey("theme_preference")
        val BUTTON_SIZE_DP = intPreferencesKey("floating_button_size_dp")
        val BUTTON_OPACITY = floatPreferencesKey("floating_button_opacity")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val ONBOARDING_ACK = booleanPreferencesKey("onboarding_acknowledged")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val modelId = prefs[Keys.MODEL_ID] ?: GeminiModelOption.GEMINI_3_5_FLASH.modelId
        val lengthName = prefs[Keys.RESPONSE_LENGTH] ?: ResponseLength.MEDIUM.name
        val themeName = prefs[Keys.THEME_PREF] ?: ThemePreference.DARK.name
        val sizeDp = (prefs[Keys.BUTTON_SIZE_DP] ?: 58).coerceIn(44, 78)
        val opacity = (prefs[Keys.BUTTON_OPACITY] ?: 0.92f).coerceIn(0.35f, 1.0f)
        val vibration = prefs[Keys.VIBRATION_ENABLED] ?: true
        val onboardingAck = prefs[Keys.ONBOARDING_ACK] ?: false

        AppSettings(
            modelOption = GeminiModelOption.fromId(modelId),
            responseLength = runCatching { ResponseLength.valueOf(lengthName) }.getOrDefault(ResponseLength.MEDIUM),
            themePreference = runCatching { ThemePreference.valueOf(themeName) }.getOrDefault(ThemePreference.DARK),
            floatingButtonSizeDp = sizeDp,
            floatingButtonOpacity = opacity,
            vibrationEnabled = vibration,
            onboardingAcknowledged = onboardingAck
        )
    }

    suspend fun setModelOption(option: GeminiModelOption) {
        context.dataStore.edit { it[Keys.MODEL_ID] = option.modelId }
    }

    suspend fun setResponseLength(length: ResponseLength) {
        context.dataStore.edit { it[Keys.RESPONSE_LENGTH] = length.name }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_PREF] = theme.name }
    }

    suspend fun setFloatingButtonSizeDp(sizeDp: Int) {
        context.dataStore.edit { it[Keys.BUTTON_SIZE_DP] = sizeDp.coerceIn(44, 78) }
    }

    suspend fun setFloatingButtonOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.BUTTON_OPACITY] = opacity.coerceIn(0.35f, 1.0f) }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setOnboardingAcknowledged(acknowledged: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_ACK] = acknowledged }
    }

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
