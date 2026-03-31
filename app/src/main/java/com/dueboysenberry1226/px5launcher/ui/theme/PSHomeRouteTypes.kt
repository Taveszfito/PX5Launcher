@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi

internal enum class HomeSection {
    TOPBAR,
    TOP,
    ACTIONS,
    WIDGETS,
    NOTIFS
}

internal enum class BottomPanel {
    WIDGETS,
    CALENDAR,
    MUSIC
}

internal data class BottomPanelPosition(
    val panel: BottomPanel,
    val col: Int,
    val row: Int
)

internal const val PS_HOME_WIDGET_HOST_ID = 1024