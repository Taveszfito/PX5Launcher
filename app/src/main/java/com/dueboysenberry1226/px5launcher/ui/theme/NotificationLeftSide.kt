@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.NotificationsRepository
import com.dueboysenberry1226.px5launcher.data.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private sealed interface PendingNotifFocusTarget {
    data class Item(val id: String) : PendingNotifFocusTarget
    data object BottomButton : PendingNotifFocusTarget
}

@Composable
fun NotificationLeftSide(
    modifier: Modifier,
    liveNotifications: List<PX5NotificationItem>,
    historyNotifications: List<PX5NotificationItem>,
    historyMode: Boolean,
    enterFocusTick: Int,
    onDismissOne: (String) -> Unit,
    onClearAll: () -> Unit,
    onToggleHistoryMode: () -> Unit,
    colors: PaneColors,
    onFocusEdgeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val settingsRepo = remember(context) { SettingsRepository(context) }
    val vibrationEnabled by settingsRepo.vibrationEnabledFlow.collectAsState(initial = true)

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    fun hTick() {
        if (vibrationEnabled) Haptics.tick(context)
    }

    val items = if (historyMode) historyNotifications else liveNotifications
    val shape = RoundedCornerShape(22.dp)

    val firstBottomButtonFR = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val itemFocusRequesters = remember {
        mutableStateMapOf<String, FocusRequester>()
    }

    val currentIds = items.map { it.id }

    LaunchedEffect(currentIds) {
        val validIds = currentIds.toSet()
        val toRemove = itemFocusRequesters.keys.filter { it !in validIds }
        toRemove.forEach { itemFocusRequesters.remove(it) }

        currentIds.forEach { id ->
            if (itemFocusRequesters[id] == null) {
                itemFocusRequesters[id] = FocusRequester()
            }
        }
    }

    var pendingFocusTarget by remember {
        mutableStateOf<PendingNotifFocusTarget?>(null)
    }

    LaunchedEffect(enterFocusTick, historyMode) {
        if (enterFocusTick <= 0) return@LaunchedEffect

        yield()
        yield()

        if (items.isEmpty()) {
            firstBottomButtonFR.requestFocus()
        } else {
            itemFocusRequesters[items.first().id]?.requestFocus()
        }
    }

    LaunchedEffect(currentIds, pendingFocusTarget) {
        val target = pendingFocusTarget ?: return@LaunchedEffect

        yield()
        yield()

        when (target) {
            is PendingNotifFocusTarget.Item -> {
                val requester = itemFocusRequesters[target.id]
                if (requester != null) {
                    requester.requestFocus()
                } else {
                    firstBottomButtonFR.requestFocus()
                }
            }

            PendingNotifFocusTarget.BottomButton -> {
                firstBottomButtonFR.requestFocus()
            }
        }

        pendingFocusTarget = null
    }

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
                    text = stringResource(R.string.notifications_empty),
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    val rowFocusRequester = itemFocusRequesters[item.id]
                        ?: remember(item.id) { FocusRequester() }.also {
                            itemFocusRequesters[item.id] = it
                        }

                    NotificationRow(
                        item = item,
                        historyMode = historyMode,
                        listState = listState,
                        itemIndex = index,
                        focusRequester = rowFocusRequester,
                        onLaunch = {
                            hClick()
                            NotificationsRepository.launch(item.id, fromHistory = historyMode)
                        },
                        onDelete = {
                            hClick()

                            val nextItemId = items.getOrNull(index + 1)?.id
                            pendingFocusTarget = if (nextItemId != null) {
                                PendingNotifFocusTarget.Item(nextItemId)
                            } else {
                                PendingNotifFocusTarget.BottomButton
                            }

                            if (historyMode) {
                                NotificationsRepository.removeFromHistory(item.id)
                            } else {
                                NotificationsRepository.dismissLiveToHistory(item.id)
                            }

                            onDismissOne(item.id)
                        },
                        colors = colors,
                        isFirst = index == 0,
                        onTopEdgeReached = onFocusEdgeChanged,
                        onFocusTick = { hTick() }
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
                text = if (historyMode) {
                    stringResource(R.string.common_back)
                } else {
                    stringResource(R.string.notifications_history)
                },
                onClick = {
                    hClick()
                    onToggleHistoryMode()
                },
                colors = colors,
                focusRequester = firstBottomButtonFR,
                onFocusChanged = { focused ->
                    if (focused) hTick()
                    if (items.isEmpty()) onFocusEdgeChanged(focused)
                }
            )

            FocusableBottomButton(
                text = stringResource(R.string.notifications_clear_all_short),
                onClick = {
                    hClick()
                    onClearAll()
                },
                colors = colors,
                onFocusChanged = { focused ->
                    if (focused) hTick()
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
    listState: LazyListState,
    itemIndex: Int,
    focusRequester: FocusRequester,
    onLaunch: () -> Unit,
    onDelete: () -> Unit,
    colors: PaneColors,
    isFirst: Boolean,
    onTopEdgeReached: (Boolean) -> Unit,
    onFocusTick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FocusableCard(
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            onClick = onLaunch,
            colors = colors,
            onFocusChanged = { focused ->
                if (focused) {
                    onFocusTick()
                    onTopEdgeReached(isFirst)
                } else if (isFirst) {
                    onTopEdgeReached(false)
                }
            },
            onFocused = {
                listState.animateScrollToItem(itemIndex)
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
                        text = stringResource(
                            R.string.notifications_time_bullet,
                            formatTimeStamp(item.postedAtMillis)
                        ),
                        color = colors.subtle.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        FocusableCard(
            modifier = Modifier.size(width = 58.dp, height = 48.dp),
            onClick = onDelete,
            colors = colors,
            onFocusChanged = { focused ->
                if (focused) {
                    onFocusTick()
                    scope.launch {
                        listState.animateScrollToItem(itemIndex)
                    }
                }
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🗑",
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
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
    onFocused: (suspend () -> Unit)? = null,
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

    LaunchedEffect(focused) {
        if (focused) {
            onFocused?.invoke()
        }
    }
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