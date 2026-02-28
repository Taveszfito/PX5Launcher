@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.SettingsRepository

@Composable
fun NotificationRightSide(
    modifier: Modifier,
    tilesState: QuickTilesState,
    onTileClick: (QuickTileType) -> Unit,
    onTileRemove: (slotIndex: Int) -> Unit,
    onTileAssign: (slotIndex: Int, type: QuickTileType) -> Unit,
    colors: PaneColors,
    onTopRowFocusEdgeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val settingsRepo = remember(context) { SettingsRepository(context) }
    val vibrationEnabled by settingsRepo.vibrationEnabledFlow.collectAsState(initial = true)

    fun hClick() { if (vibrationEnabled) Haptics.click(context) }
    fun hTick() { if (vibrationEnabled) Haptics.tick(context) }

    var pickerOpen by remember { mutableStateOf(false) }
    var pickerTargetSlot by remember { mutableStateOf(-1) }

    val used = remember(tilesState.slots) { tilesState.slots.filterNotNull().toSet() }
    val available = remember(used) { QuickTileType.entries.filter { it !in used } }

    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(2.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(18.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val cellHeight = 64.dp

            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0 until 5) {
                        val idx = row * 5 + col
                        val type = tilesState.slots[idx]

                        QuickTileCell(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight),
                            type = type,
                            onClick = {
                                hClick()
                                if (type == null) {
                                    pickerTargetSlot = idx
                                    pickerOpen = true
                                } else {
                                    onTileClick(type)
                                }
                            },
                            onRemove = {
                                hClick()
                                onTileRemove(idx)
                            },
                            colors = colors,
                            isTopRow = (row == 0),
                            onTopRowFocusEdgeChanged = onTopRowFocusEdgeChanged,
                            onFocusTick = { hTick() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }

    if (pickerOpen) {
        QuickTilePickerDialog(
            available = available,
            onCancel = {
                hClick()
                pickerOpen = false
                pickerTargetSlot = -1
            },
            onConfirm = { selected: QuickTileType ->
                hClick()
                if (pickerTargetSlot >= 0) {
                    onTileAssign(pickerTargetSlot, selected)
                }
                pickerOpen = false
                pickerTargetSlot = -1
            },
            colors = colors,
            onPickClick = { hClick() },
            onPickTick = { hTick() }
        )
    }
}

@Composable
private fun QuickTileCell(
    modifier: Modifier,
    type: QuickTileType?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    colors: PaneColors,
    isTopRow: Boolean,
    onTopRowFocusEdgeChanged: (Boolean) -> Unit,
    onFocusTick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.22f) else colors.cardAlt)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else colors.stroke,
                shape = shape
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocusTick()
                if (isTopRow) onTopRowFocusEdgeChanged(it.isFocused)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (type == null) {
            Text(
                text = "+",
                color = colors.subtle,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = type.label(),
                    tint = colors.text,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = type.label(),
                    color = colors.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "🗑  ${stringResource(R.string.common_delete)}",
                            color = Color(0xFFFF8080)
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickTilePickerDialog(
    available: List<QuickTileType>,
    onCancel: () -> Unit,
    onConfirm: (QuickTileType) -> Unit,
    colors: PaneColors,
    onPickClick: () -> Unit,
    onPickTick: () -> Unit,
) {
    var selected by remember { mutableStateOf<QuickTileType?>(available.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(stringResource(R.string.qs_add_tile_title), color = colors.text)
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(available) { t ->
                    val isSel = selected == t
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSel) Color.White.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                onPickClick()
                                selected = t
                            }
                            .padding(12.dp)
                    ) {
                        Text(t.label(), color = colors.text)
                    }
                }
            }

            LaunchedEffect(selected) {
                // ha DPAD-ozással / új kijelöléssel is változik, kapjon egy tick-et
                if (selected != null) onPickTick()
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null
            ) {
                Text(stringResource(R.string.common_select))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.notifications_back))
            }
        }
    )
}