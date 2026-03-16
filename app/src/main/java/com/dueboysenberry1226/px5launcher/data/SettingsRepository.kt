package com.dueboysenberry1226.px5launcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val PREF_CLOCK_24H = booleanPreferencesKey("settings_clock_24h")
private val PREF_SHOW_BIG_APP_NAME = booleanPreferencesKey("settings_show_big_app_name")
private val PREF_ACCENT_FROM_APP_ICON = booleanPreferencesKey("settings_accent_from_app_icon")
private val PREF_LANGUAGE_CODE = stringPreferencesKey("settings_language_code")

private val PREF_BUTTON_LAYOUT = stringPreferencesKey("settings_button_layout") // PS / XBOX / TV
private val PREF_VIBRATION_ENABLED = booleanPreferencesKey("settings_vibration_enabled")

// ✅ NEW
private val PREF_VIBRATION_STRENGTH = intPreferencesKey("settings_vibration_strength") // 0..5

private val PREF_ALLAPPS_COLUMNS = intPreferencesKey("settings_allapps_columns")

enum class ButtonLayout { PS, XBOX, TV }

class SettingsRepository(private val context: Context) {

    val clock24hFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_CLOCK_24H] ?: true }

    val showBigAppNameFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_SHOW_BIG_APP_NAME] ?: true }

    val accentFromAppIconFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_ACCENT_FROM_APP_ICON] ?: true }

    val languageCodeFlow: Flow<String> =
        context.px5DataStore.data.map { it[PREF_LANGUAGE_CODE] ?: "system" }

    val buttonLayoutFlow: Flow<ButtonLayout> =
        context.px5DataStore.data.map { prefs ->
            when ((prefs[PREF_BUTTON_LAYOUT] ?: "PS").uppercase()) {
                "XBOX" -> ButtonLayout.XBOX
                "TV" -> ButtonLayout.TV
                else -> ButtonLayout.PS
            }
        }

    val vibrationEnabledFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_VIBRATION_ENABLED] ?: true }

    // ✅ NEW: 0..5, default 1 (a mostani, legkisebb)
    val vibrationStrengthFlow: Flow<Int> =
        context.px5DataStore.data.map { (it[PREF_VIBRATION_STRENGTH] ?: 1).coerceIn(0, 5) }

    // ✅ All Apps oszlopszám: 2..5 (régi 6/10 értékek automatikusan visszajönnek 5-re)
    val allAppsColumnsFlow: Flow<Int> =
        context.px5DataStore.data.map { (it[PREF_ALLAPPS_COLUMNS] ?: 4).coerceIn(2, 5) }

    // --- setters ---
    suspend fun setClock24h(value: Boolean) = setBool(PREF_CLOCK_24H, value)
    suspend fun setShowBigAppName(value: Boolean) = setBool(PREF_SHOW_BIG_APP_NAME, value)
    suspend fun setAccentFromAppIcon(value: Boolean) = setBool(PREF_ACCENT_FROM_APP_ICON, value)
    suspend fun setLanguageCode(value: String) = setString(PREF_LANGUAGE_CODE, value)

    suspend fun setButtonLayout(value: ButtonLayout) = setString(PREF_BUTTON_LAYOUT, value.name)
    suspend fun setVibrationEnabled(value: Boolean) = setBool(PREF_VIBRATION_ENABLED, value)

    // ✅ NEW
    suspend fun setVibrationStrength(value: Int) =
        setInt(PREF_VIBRATION_STRENGTH, value.coerceIn(0, 5))

    // ✅ NEW: 2..5
    suspend fun setAllAppsColumns(value: Int) =
        setInt(PREF_ALLAPPS_COLUMNS, value.coerceIn(2, 5))

    private suspend fun setBool(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        value: Boolean
    ) {
        context.px5DataStore.edit { it[key] = value }
    }

    private suspend fun setInt(
        key: androidx.datastore.preferences.core.Preferences.Key<Int>,
        value: Int
    ) {
        context.px5DataStore.edit { it[key] = value }
    }

    private suspend fun setString(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String
    ) {
        context.px5DataStore.edit { it[key] = value }
    }

}