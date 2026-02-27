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
private val PREF_WALLPAPER_URI = stringPreferencesKey("settings_wallpaper_uri")
private val PREF_LANGUAGE_CODE = stringPreferencesKey("settings_language_code")

private val PREF_AMBIENT_ENABLED = booleanPreferencesKey("settings_ambient_enabled")
private val PREF_AMBIENT_MUTED = booleanPreferencesKey("settings_ambient_muted")
private val PREF_AMBIENT_VOLUME = intPreferencesKey("settings_ambient_volume")
private val PREF_AMBIENT_SOUND_URI = stringPreferencesKey("settings_ambient_sound_uri")

private val PREF_CLICK_ENABLED = booleanPreferencesKey("settings_click_enabled")
private val PREF_CLICK_VOLUME = intPreferencesKey("settings_click_volume")

private val PREF_BUTTON_LAYOUT = stringPreferencesKey("settings_button_layout") // PS / XBOX / TV
private val PREF_VIBRATION_ENABLED = booleanPreferencesKey("settings_vibration_enabled")

private val PREF_ALLAPPS_COLUMNS = intPreferencesKey("settings_allapps_columns")
private val PREF_RECENTS_MAX = intPreferencesKey("settings_recents_max")

enum class ButtonLayout { PS, XBOX, TV }

class SettingsRepository(private val context: Context) {

    val clock24hFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_CLOCK_24H] ?: true }

    val showBigAppNameFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_SHOW_BIG_APP_NAME] ?: true }

    val accentFromAppIconFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_ACCENT_FROM_APP_ICON] ?: true }

    val wallpaperUriFlow: Flow<String?> =
        context.px5DataStore.data.map { it[PREF_WALLPAPER_URI] }

    val languageCodeFlow: Flow<String> =
        context.px5DataStore.data.map { it[PREF_LANGUAGE_CODE] ?: "system" }

    val ambientEnabledFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_AMBIENT_ENABLED] ?: false }

    val ambientMutedFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_AMBIENT_MUTED] ?: false }

    val ambientVolumeFlow: Flow<Int> =
        context.px5DataStore.data.map { it[PREF_AMBIENT_VOLUME] ?: 35 }

    val ambientSoundUriFlow: Flow<String?> =
        context.px5DataStore.data.map { it[PREF_AMBIENT_SOUND_URI] }

    val clickEnabledFlow: Flow<Boolean> =
        context.px5DataStore.data.map { it[PREF_CLICK_ENABLED] ?: true }

    val clickVolumeFlow: Flow<Int> =
        context.px5DataStore.data.map { it[PREF_CLICK_VOLUME] ?: 35 }

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

    val allAppsColumnsFlow: Flow<Int> =
        context.px5DataStore.data.map { it[PREF_ALLAPPS_COLUMNS] ?: 6 }

    val recentsMaxFlow: Flow<Int> =
        context.px5DataStore.data.map { it[PREF_RECENTS_MAX] ?: 30 }

    // --- setters ---
    suspend fun setClock24h(value: Boolean) = setBool(PREF_CLOCK_24H, value)
    suspend fun setShowBigAppName(value: Boolean) = setBool(PREF_SHOW_BIG_APP_NAME, value)
    suspend fun setAccentFromAppIcon(value: Boolean) = setBool(PREF_ACCENT_FROM_APP_ICON, value)
    suspend fun setWallpaperUri(value: String?) = setStringNullable(PREF_WALLPAPER_URI, value)
    suspend fun setLanguageCode(value: String) = setString(PREF_LANGUAGE_CODE, value)

    suspend fun setAmbientEnabled(value: Boolean) = setBool(PREF_AMBIENT_ENABLED, value)
    suspend fun setAmbientMuted(value: Boolean) = setBool(PREF_AMBIENT_MUTED, value)
    suspend fun setAmbientVolume(value: Int) = setInt(PREF_AMBIENT_VOLUME, value.coerceIn(0, 100))
    suspend fun setAmbientSoundUri(value: String?) = setStringNullable(PREF_AMBIENT_SOUND_URI, value)

    suspend fun setClickEnabled(value: Boolean) = setBool(PREF_CLICK_ENABLED, value)
    suspend fun setClickVolume(value: Int) = setInt(PREF_CLICK_VOLUME, value.coerceIn(0, 100))

    suspend fun setButtonLayout(value: ButtonLayout) = setString(PREF_BUTTON_LAYOUT, value.name)
    suspend fun setVibrationEnabled(value: Boolean) = setBool(PREF_VIBRATION_ENABLED, value)

    suspend fun setAllAppsColumns(value: Int) = setInt(PREF_ALLAPPS_COLUMNS, value.coerceIn(3, 10))
    suspend fun setRecentsMax(value: Int) = setInt(PREF_RECENTS_MAX, value.coerceIn(5, 200))

    private suspend fun setBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.px5DataStore.edit { it[key] = value }
    }

    private suspend fun setInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        context.px5DataStore.edit { it[key] = value }
    }

    private suspend fun setString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.px5DataStore.edit { it[key] = value }
    }

    private suspend fun setStringNullable(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String?) {
        context.px5DataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(key) else prefs[key] = value
        }
    }
}