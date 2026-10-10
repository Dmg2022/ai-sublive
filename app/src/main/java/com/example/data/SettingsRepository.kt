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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "livesub_settings")

data class AppSettings(
    val sourceLanguage: String = "en",
    val targetLanguage: String = "vi",
    val fontSizeSp: Float = 20f,
    val backgroundOpacity: Float = 0.85f,
    val textColorHex: String = "#FFFFFF",
    val maxLines: Int = 3,
    val showOriginalText: Boolean = true,
    val translatePartial: Boolean = false,
    val vadSensitivityRms: Float = 180f,
    val wifiOnlyDownload: Boolean = true,
    val saveSrtHistory: Boolean = false,
    val clickThroughEnabled: Boolean = false,
    val overlayPosX: Int = -1,
    val overlayPosY: Int = -1
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SOURCE_LANG = stringPreferencesKey("source_lang")
        val TARGET_LANG = stringPreferencesKey("target_lang")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val BG_OPACITY = floatPreferencesKey("bg_opacity")
        val TEXT_COLOR = stringPreferencesKey("text_color")
        val MAX_LINES = intPreferencesKey("max_lines")
        val SHOW_ORIGINAL = booleanPreferencesKey("show_original")
        val TRANSLATE_PARTIAL = booleanPreferencesKey("translate_partial")
        val VAD_SENSITIVITY = floatPreferencesKey("vad_sensitivity")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val SAVE_SRT = booleanPreferencesKey("save_srt")
        val CLICK_THROUGH = booleanPreferencesKey("click_through")
        val OVERLAY_X = intPreferencesKey("overlay_x")
        val OVERLAY_Y = intPreferencesKey("overlay_y")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            sourceLanguage = prefs[Keys.SOURCE_LANG] ?: "en",
            targetLanguage = prefs[Keys.TARGET_LANG] ?: "vi",
            fontSizeSp = prefs[Keys.FONT_SIZE] ?: 20f,
            backgroundOpacity = prefs[Keys.BG_OPACITY] ?: 0.85f,
            textColorHex = prefs[Keys.TEXT_COLOR] ?: "#FFFFFF",
            maxLines = prefs[Keys.MAX_LINES] ?: 3,
            showOriginalText = prefs[Keys.SHOW_ORIGINAL] ?: true,
            translatePartial = prefs[Keys.TRANSLATE_PARTIAL] ?: false,
            vadSensitivityRms = prefs[Keys.VAD_SENSITIVITY] ?: 180f,
            wifiOnlyDownload = prefs[Keys.WIFI_ONLY] ?: true,
            saveSrtHistory = prefs[Keys.SAVE_SRT] ?: false,
            clickThroughEnabled = prefs[Keys.CLICK_THROUGH] ?: false,
            overlayPosX = prefs[Keys.OVERLAY_X] ?: -1,
            overlayPosY = prefs[Keys.OVERLAY_Y] ?: -1
        )
    }

    suspend fun updateSourceLanguage(code: String) {
        context.dataStore.edit { it[Keys.SOURCE_LANG] = code }
    }

    suspend fun updateTargetLanguage(code: String) {
        context.dataStore.edit { it[Keys.TARGET_LANG] = code }
    }

    suspend fun updateFontSize(size: Float) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = size }
    }

    suspend fun updateBackgroundOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.BG_OPACITY] = opacity }
    }

    suspend fun updateShowOriginalText(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ORIGINAL] = show }
    }

    suspend fun updateTranslatePartial(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRANSLATE_PARTIAL] = enabled }
    }

    suspend fun updateVadSensitivity(rms: Float) {
        context.dataStore.edit { it[Keys.VAD_SENSITIVITY] = rms }
    }

    suspend fun updateWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = wifiOnly }
    }

    suspend fun updateSaveSrt(save: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_SRT] = save }
    }

    suspend fun updateClickThrough(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLICK_THROUGH] = enabled }
    }

    suspend fun updateOverlayPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[Keys.OVERLAY_X] = x
            it[Keys.OVERLAY_Y] = y
        }
    }
}
