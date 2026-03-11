@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import android.graphics.drawable.Drawable
import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement

internal const val COLS = 4
internal const val MAX_ROWS = 7
internal const val SLOTS = COLS * MAX_ROWS

internal data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

internal sealed class DrawerEntry {
    data class Header(val letter: String) : DrawerEntry()
    data class App(val app: AppItem, val letter: String) : DrawerEntry()
}

internal sealed class DragPayload {
    data class App(val pkg: String, val fromIndex: Int) : DragPayload()
    data class Card(val placement: PhoneCardPlacement) : DragPayload()

    internal data class DropPreview(
        val cellX: Int,
        val cellY: Int,
        val spanX: Int,
        val spanY: Int,
        val isValid: Boolean
    )

    // ✅ ÚJ: Widget drag payload (grid cell alapon)
    data class Widget(
        val widgetId: Int,
        val spanX: Int,
        val spanY: Int,
        val fromCellX: Int,
        val fromCellY: Int
    ) : DragPayload()
}

data class DropPreview(
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val isValid: Boolean
)