@file:Suppress("UnusedBoxWithConstraintsScope")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun PhoneHomeGrid(
    modifier: Modifier = Modifier,

    slots: List<String?>,
    cards: List<PhoneCardPlacement>,
    editMode: Boolean,

    resolveLabelForSlot: (String?) -> String,
    resolveIconForSlot: (String?) -> android.graphics.drawable.Drawable?,
    onRemoveFromHome: (String) -> Unit,
    onLaunch: (String) -> Unit,

    // drag state + helpers
    dragging: DragPayload?,
    setDragging: (DragPayload?) -> Unit,
    dragPointerPx: Offset,
    hasDragPointer: Boolean,
    setHasDragPointer: (Boolean) -> Unit,

    // geometry outputs to parent
    setGridTopLeftPx: (Offset) -> Unit,
    setCellSizePx: (Float) -> Unit,
    setGapHPx: (Float) -> Unit,
    setGapVPx: (Float) -> Unit,
    setRowsToShowState: (Int) -> Unit,

    // used width/cell dp outputs to parent
    setGridUsedWidth: (Dp) -> Unit,
    setPhoneCellDp: (Dp) -> Unit,

    // slot move
    moveAppToIndex: (String, Int, Int) -> Unit,

    // place error
    clearPlaceError: () -> Unit,

    // card persist helpers
    onMoveCard: (PhoneCardPlacement, Int) -> Unit,
    onDeleteCard: (PhoneCardPlacement) -> Unit,

    // add stuff popup
    addStuffOpen: Boolean,
    addStuffContent: @Composable (rowsToShow: Int, cellDp: Dp) -> Unit
) {
    val density = LocalDensity.current

    val btnH = 58.dp
    val bottomGap = 10.dp
    val bottomReserved = btnH + bottomGap

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomReserved),
        contentAlignment = Alignment.Center
    ) {
        val padX = 6.dp
        val padY = 2.dp

        val minGapH = 10.dp
        val minGapV = 12.dp

        val availableW = maxWidth - padX * 2
        val availableH = maxHeight - padY * 2

        val cellSizeFromWidth = (availableW - minGapH * (COLS - 1)) / COLS
        val cellSize = if (cellSizeFromWidth > availableH) availableH else cellSizeFromWidth

        SideEffect {
            setCellSizePx(with(density) { cellSize.toPx() })
            setGapHPx(with(density) { minGapH.toPx() })
            setGapVPx(with(density) { minGapV.toPx() })
        }

        SideEffect { setPhoneCellDp(cellSize) }

        val usedW = cellSize * COLS + minGapH * (COLS - 1)
        SideEffect { setGridUsedWidth(usedW) }

        val fitsRowsFloat = (availableH.value + minGapV.value) / (cellSize.value + minGapV.value)
        val rowsToShow = floor(fitsRowsFloat).toInt().coerceIn(1, MAX_ROWS)
        SideEffect { setRowsToShowState(rowsToShow) }

        // GRID + Cards overlay container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = padX, vertical = padY)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    setGridTopLeftPx(Offset(pos.x, pos.y))
                }
        ) {
            fun cellOffsetX(col: Int): Float = with(density) {
                val cs = cellSize.toPx()
                val gh = minGapH.toPx()
                col * (cs + gh)
            }
            fun cellOffsetY(row: Int): Float = with(density) {
                val cs = cellSize.toPx()
                val gv = minGapV.toPx()
                row * (cs + gv)
            }

            val cardW = (cellSize * 4 + minGapH * 3)
            val cardH = (cellSize * 2 + minGapV * 1)

            // 1) GRID IKONOK
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f),
                verticalArrangement = Arrangement.spacedBy(minGapV)
            ) {
                for (r in 0 until rowsToShow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(minGapH, Alignment.CenterHorizontally)
                    ) {
                        for (c in 0 until COLS) {
                            val idx = r * COLS + c
                            val id = slots.getOrNull(idx)
                            val isEmpty = (id == null)

                            val coveredByCard = cards.any { card ->
                                card.col == 0 &&
                                        r in card.row until (card.row + CARD_SPAN_Y) &&
                                        c in card.col until (card.col + CARD_SPAN_X)
                            }

                            HomeSlot(
                                id = id,
                                label = resolveLabelForSlot(id),
                                icon = resolveIconForSlot(id),
                                isEmpty = isEmpty,
                                showPlaceholder = (editMode) && !coveredByCard,
                                modifier = Modifier.size(cellSize),

                                showDelete = editMode && id != null && !coveredByCard,
                                onDelete = { if (id != null) onRemoveFromHome(id) },

                                canDrag = !coveredByCard && id != null,
                                onStartDrag = {
                                    if (id == null) return@HomeSlot
                                    clearPlaceError()
                                    setDragging(DragPayload.App(pkg = id, fromIndex = idx))
                                    setHasDragPointer(false)
                                },

                                onClick = {
                                    if (editMode) return@HomeSlot
                                    if (coveredByCard) return@HomeSlot
                                    if (id != null) onLaunch(id)
                                }
                            )
                        }
                    }
                }
            }

            // 2) KÁRTYÁK
            cards.forEach { card ->
                if (card.col != 0) return@forEach
                if (card.row < 0 || card.row + 1 >= rowsToShow) return@forEach

                val ox = cellOffsetX(card.col).roundToInt()
                val oy = cellOffsetY(card.row).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(ox, oy) }
                        .width(cardW)
                        .height(cardH)
                        .zIndex(1f)
                        .pointerInput(card, editMode, rowsToShow) {
                            if (editMode) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { setDragging(DragPayload.Card(card)) },
                                    onDrag = { change, dragAmount ->
                                        change.consumeAllChanges()

                                        val stepY = (with(density) { cellSize.toPx() } + with(density) { minGapV.toPx() })
                                        val deltaRows = (dragAmount.y / stepY)
                                        val targetRow = (card.row + deltaRows).roundToInt()

                                        onMoveCard(card, targetRow)
                                    },
                                    onDragEnd = { setDragging(null) },
                                    onDragCancel = { setDragging(null) }
                                )
                            }
                        }
                ) {
                    PhoneHomeCard(type = card.type, modifier = Modifier.fillMaxSize())

                    if (editMode) {
                        DeleteBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            onClick = { onDeleteCard(card) }
                        )
                    }
                }
            }

            // 3) DRAG PREVIEW
            val drag = dragging
            if (drag is DragPayload.App && hasDragPointer) {
                val localX = (dragPointerPx.x).roundToInt()
                val localY = (dragPointerPx.y).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(localX - 24, localY - 24) }
                        .size(48.dp)
                        .zIndex(999f)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) { }

                    Text(
                        text = "⠿",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        if (addStuffOpen) {
            addStuffContent(rowsToShow, cellSize)
        }
    }
}