package com.dueboysenberry1226.px5launcher.ui.theme

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.dp

@Composable
fun NotificationsTabScreen(
    modifier: Modifier = Modifier,

    // Left side
    liveNotifications: List<PX5NotificationItem>,
    historyNotifications: List<PX5NotificationItem>,
    enterFocusTick: Int,
    historyMode: Boolean,
    onDismissOne: (id: String, fromHistory: Boolean) -> Unit,
    onClearAll: (fromHistory: Boolean) -> Unit,
    onToggleHistoryMode: () -> Unit,

    // Right side
    tilesState: QuickTilesState,
    onTileClick: (QuickTileType) -> Unit,
    onTileRemove: (slotIndex: Int) -> Unit,
    onTileAssign: (slotIndex: Int, type: QuickTileType) -> Unit,

    // Focus edge jelzések a PSHomeRoute felé
    onLeftButtonsFocusEdgeChanged: (Boolean) -> Unit,
    onQsTopRowFocusEdgeChanged: (Boolean) -> Unit,

    // ÚJ
    registerKeyHandler: (handler: (KeyEvent) -> Boolean) -> Unit,
    onRequestMoveToTopbar: () -> Unit,
) {
    val colors = remember {
        PaneColors(
            card = Color.White.copy(alpha = 0.06f),
            cardAlt = Color.White.copy(alpha = 0.05f),
            stroke = Color.White.copy(alpha = 0.12f),
            text = Color.White,
            subtle = Color.White.copy(alpha = 0.70f)
        )
    }

    var leftEdgeFocused by remember { mutableStateOf(false) }
    var qsTopEdgeFocused by remember { mutableStateOf(false) }

    val internalKeyHandler: (KeyEvent) -> Boolean = { e ->
        val nk = e.nativeKeyEvent
        if (nk.action != AndroidKeyEvent.ACTION_DOWN) {
            false
        } else {
            when (nk.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    if (leftEdgeFocused || qsTopEdgeFocused) {
                        onRequestMoveToTopbar()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    SideEffect {
        registerKeyHandler(internalKeyHandler)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        NotificationLeftSide(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            liveNotifications = liveNotifications,
            enterFocusTick = enterFocusTick,
            historyNotifications = historyNotifications,
            historyMode = historyMode,
            onDismissOne = { id -> onDismissOne(id, historyMode) },
            onClearAll = { onClearAll(historyMode) },
            onToggleHistoryMode = onToggleHistoryMode,
            colors = colors,
            onFocusEdgeChanged = { focused ->
                leftEdgeFocused = focused
                onLeftButtonsFocusEdgeChanged(focused)
            },
        )

        NotificationRightSide(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            tilesState = tilesState,
            onTileClick = onTileClick,
            onTileRemove = onTileRemove,
            onTileAssign = onTileAssign,
            colors = colors,
            onTopRowFocusEdgeChanged = { focused ->
                qsTopEdgeFocused = focused
                onQsTopRowFocusEdgeChanged(focused)
            }
        )
    }
}