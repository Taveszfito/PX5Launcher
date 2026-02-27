@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui

import android.app.PendingIntent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    @StringRes val labelRes: Int
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

@Composable
fun SmallPillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: PaneColors,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (enabled) Color(0x22FFFFFF) else Color(0x11FFFFFF)
    val stroke = if (enabled) Color(0x44FFFFFF) else Color(0x22FFFFFF)

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, stroke, shape)
            .combinedClickable(
                onClick = { if (enabled) onClick() },
                onLongClick = {}
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) colors.text else colors.subtle,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SmallSquareButton(
    label: String,
    onClick: () -> Unit,
    colors: PaneColors,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0x22FFFFFF))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .combinedClickable(onClick = onClick, onLongClick = {})
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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