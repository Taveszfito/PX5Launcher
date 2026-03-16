@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import android.app.PendingIntent
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.dueboysenberry1226.px5launcher.R

// =============================
// Shared models
// =============================

data class PX5NotificationItem(
    val id: String,
    val appName: String,
    val message: String,
    val postedAtMillis: Long,
    val packageName: String = "",
    val contentIntent: PendingIntent? = null,
    val appIconTint: Color = Color(0xFF2A5BD7),
)

enum class QuickTileType(
    val icon: ImageVector,
    @get:StringRes val labelRes: Int
) {
    WIFI(Icons.Filled.Wifi, R.string.qs_tile_wifi),
    BT(Icons.Filled.Bluetooth, R.string.qs_tile_bluetooth),
    ROTATION(Icons.Filled.ScreenRotation, R.string.qs_tile_rotation),
    STB(Icons.Filled.Tune, R.string.qs_tile_settings),
    FLASHLIGHT(Icons.Filled.FlashlightOn, R.string.qs_tile_flashlight),
    AIRPLANE(Icons.Filled.AirplanemodeActive, R.string.qs_tile_airplane),
    LOCATION(Icons.Filled.LocationOn, R.string.qs_tile_location),
    DND(Icons.Filled.DoNotDisturbOn, R.string.qs_tile_dnd),
}

// használat: type.label()
@Composable
fun QuickTileType.label(): String = stringResource(labelRes)

data class QuickTilesState(
    // 20 slot = 4x5 (row-major): index = row*5 + col
    val slots: List<QuickTileType?> = List(20) { null }
) {
    init {
        require(slots.size == 20) { "QuickTilesState.slots must be size 20 (4x5)" }
    }
}

// =============================
// Shared theme-ish colors (local)
// =============================

data class PaneColors(
    val card: Color,
    val cardAlt: Color,
    val stroke: Color,
    val text: Color,
    val subtle: Color,
)

// =============================
// Shared small UI helpers
// =============================

// =============================
// Timestamp (history)
// =============================

fun formatTimeStamp(ms: Long): String {
    // Placeholder – később lecserélheted rendes dátum formátumra
    val s = (ms / 1000L).toInt()
    val min = (s / 60) % 60
    val hour = (s / 3600) % 24
    return "??-??-?? ${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
}