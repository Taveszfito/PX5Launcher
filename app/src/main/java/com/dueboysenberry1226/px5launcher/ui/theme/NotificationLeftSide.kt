@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.data.NotificationsRepository

@Composable
fun NotificationLeftSide(
    modifier: Modifier,
    liveNotifications: List<PX5NotificationItem>,
    historyNotifications: List<PX5NotificationItem>,
    historyMode: Boolean,
    onDismissOne: (String) -> Unit,
    onClearAll: () -> Unit,
    onToggleHistoryMode: () -> Unit,
    colors: PaneColors,
    onFocusEdgeChanged: (Boolean) -> Unit,
) {
    val items = if (historyMode) historyNotifications else liveNotifications
    val shape = RoundedCornerShape(22.dp)

    val firstBottomButtonFR = remember { FocusRequester() }

    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(2.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nincs értesítés",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    NotificationRow(
                        item = item,
                        historyMode = historyMode,
                        onLaunch = {
                            NotificationsRepository.launch(item.id, fromHistory = historyMode)
                        },
                        onDelete = {
                            // delete gomb:
                            // live -> dismiss + history
                            // history -> remove history entry
                            if (historyMode) {
                                NotificationsRepository.removeFromHistory(item.id)
                            } else {
                                NotificationsRepository.dismissLiveToHistory(item.id)
                            }
                            // ha még nálad van külön UI listakezelés:
                            onDismissOne(item.id)
                        },
                        colors = colors,
                        isFirst = index == 0,
                        onTopEdgeReached = onFocusEdgeChanged
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FocusableBottomButton(
                text = if (historyMode) "Vissza" else "Előzmények",
                onClick = onToggleHistoryMode,
                colors = colors,
                focusRequester = firstBottomButtonFR,
                onFocusChanged = { focused ->
                    // ✅ csak akkor jelezzük a "topbar fel" engedélyt, ha NINCS értesítés
                    if (items.isEmpty()) onFocusEdgeChanged(focused)
                }
            )

            FocusableBottomButton(
                text = "Össz. Törl.",
                onClick = onClearAll,
                colors = colors,
                onFocusChanged = { focused ->
                    if (items.isEmpty()) onFocusEdgeChanged(focused)
                }
            )
        }
    }
}

@Composable
private fun NotificationRow(
    item: PX5NotificationItem,
    historyMode: Boolean,
    onLaunch: () -> Unit,
    onDelete: () -> Unit,
    colors: PaneColors,
    isFirst: Boolean,
    onTopEdgeReached: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🔵 launch area (külön fókusz target)
        FocusableCard(
            modifier = Modifier.weight(1f),
            onClick = onLaunch,
            colors = colors,
            onFocusChanged = { focused ->
                if (isFirst) onTopEdgeReached(focused)
            }
        ) {
            Column {
                Text(
                    text = item.appName,
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.message,
                    color = colors.subtle,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (historyMode) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• ${formatTimeStamp(item.postedAtMillis)}",
                        color = colors.subtle.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // 🔴 delete button (külön fókusz target)
        FocusableCard(
            modifier = Modifier.size(width = 58.dp, height = 48.dp),
            onClick = onDelete,
            colors = colors,
            onFocusChanged = {}
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🗑", color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FocusableCard(
    modifier: Modifier,
    onClick: () -> Unit,
    colors: PaneColors,
    onFocusChanged: (Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (focused) Color.White.copy(alpha = 0.22f) else colors.cardAlt
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else colors.stroke,
                shape = shape
            )
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart,
        content = content
    )
}

@Composable
private fun FocusableBottomButton(
    text: String,
    onClick: () -> Unit,
    colors: PaneColors,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(
                if (focused) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.4f),
                shape = shape
            )
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}