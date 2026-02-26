package com.dueboysenberry1226.px5launcher.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * FONTOS: csak egyetlen DataStore legyen a px5_launcher_prefs fájlra!
 * Ezt használd minden repository-ból.
 */
val Context.px5DataStore by preferencesDataStore(name = "px5_launcher_prefs")
