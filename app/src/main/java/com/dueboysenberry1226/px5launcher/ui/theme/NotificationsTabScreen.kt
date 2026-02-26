package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NotificationsTabScreen(
    modifier: Modifier = Modifier,

    // Left side
    liveNotifications: List<PX5NotificationItem>,
    historyNotifications: List<PX5NotificationItem>,
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
) {
    val colors = PaneColors(
        card = Color.White.copy(alpha = 0.06f),
        cardAlt = Color.White.copy(alpha = 0.05f),
        stroke = Color.White.copy(alpha = 0.12f),
        text = Color.White,
        subtle = Color.White.copy(alpha = 0.70f)
    )

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
            historyNotifications = historyNotifications,
            historyMode = historyMode,
            onDismissOne = { id -> onDismissOne(id, historyMode) },
            onClearAll = { onClearAll(historyMode) },
            onToggleHistoryMode = onToggleHistoryMode,
            colors = colors,
            onFocusEdgeChanged = onLeftButtonsFocusEdgeChanged,
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
            onTopRowFocusEdgeChanged = onQsTopRowFocusEdgeChanged
        )
    }
}