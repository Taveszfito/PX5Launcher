@file:Suppress("UnusedBoxWithConstraintsScope")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun PhoneHomeGrid(
    modifier: Modifier = Modifier,

    slots: List<String?>,
    cards: List<PhoneCardPlacement>,
    widgets: List<WidgetPlacement>,

    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,

    editMode: Boolean,

    resolveLabelForSlot: (String?) -> String,
    resolveIconForSlot: (String?) -> android.graphics.drawable.Drawable?,
    onRemoveFromHome: (String) -> Unit,
    onLaunch: (String) -> Unit,

    onDeleteWidget: (Int) -> Unit,

    // drag state + helpers
    dragging: DragPayload?,
    setDragging: (DragPayload?) -> Unit,
    setDragPointer: (Offset) -> Unit,
    finishDragAt: (Offset) -> Unit,
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
    var gridTopLeftPx by remember { mutableStateOf(Offset.Zero) }

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

        // GRID + overlay container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = padX, vertical = padY)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val rootPos = Offset(pos.x, pos.y)
                    gridTopLeftPx = rootPos
                    setGridTopLeftPx(rootPos)
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

            fun isCoveredByWidget(r: Int, c: Int): Boolean {
                return widgets.any { w ->
                    r in w.cellY until (w.cellY + w.spanY) &&
                            c in w.cellX until (w.cellX + w.spanX)
                }
            }

            fun isCoveredByCard(r: Int, c: Int): Boolean {
                return cards.any { card ->
                    card.col == 0 &&
                            r in card.row until (card.row + CARD_SPAN_Y) &&
                            c in card.col until (card.col + CARD_SPAN_X)
                }
            }

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

                            val coveredByCard = isCoveredByCard(r, c)
                            val coveredByWidget = isCoveredByWidget(r, c)
                            val covered = coveredByCard || coveredByWidget

                            HomeSlot(
                                id = id,
                                label = resolveLabelForSlot(id),
                                icon = resolveIconForSlot(id),
                                isEmpty = isEmpty,
                                showPlaceholder = (editMode) && !covered,
                                modifier = Modifier.size(cellSize),

                                showDelete = editMode && id != null && !covered,
                                onDelete = { if (id != null) onRemoveFromHome(id) },

                                canDrag = !covered && id != null,
                                onStartDrag = { rootPointer ->
                                    if (id == null) return@HomeSlot
                                    clearPlaceError()
                                    setDragging(DragPayload.App(pkg = id, fromIndex = idx))
                                    setDragPointer(rootPointer)
                                    setHasDragPointer(true)
                                },
                                onDragMove = { rootPointer ->
                                    setDragPointer(rootPointer)
                                    setHasDragPointer(true)
                                },
                                onEndDrag = { rootPointer ->
                                    setDragPointer(rootPointer)
                                    setHasDragPointer(true)
                                    finishDragAt(rootPointer)
                                },

                                onClick = {
                                    if (editMode) return@HomeSlot
                                    if (covered) return@HomeSlot
                                    if (id != null) onLaunch(id)
                                }
                            )
                        }
                    }
                }
            }

            // 2) WIDGETEK (cards alatt, drag preview felett)
            widgets.forEach { w ->
                if (w.cellY < 0 || w.cellY >= rowsToShow) return@forEach
                if (w.cellX < 0 || w.cellX >= COLS) return@forEach
                if (w.cellX + w.spanX > COLS) return@forEach
                if (w.cellY + w.spanY > rowsToShow) return@forEach

                val ox = cellOffsetX(w.cellX).roundToInt()
                val oy = cellOffsetY(w.cellY).roundToInt()

                val ww = (cellSize * w.spanX + minGapH * (w.spanX - 1))
                val hh = (cellSize * w.spanY + minGapV * (w.spanY - 1))

                WidgetDragSurface(
                    enabled = editMode,
                    key = w.appWidgetId,
                    onStartDrag = { rootPointer ->
                        clearPlaceError()
                        setDragging(
                            DragPayload.Widget(
                                widgetId = w.appWidgetId,
                                spanX = w.spanX,
                                spanY = w.spanY,
                                fromCellX = w.cellX,
                                fromCellY = w.cellY
                            )
                        )
                        setDragPointer(rootPointer)
                        setHasDragPointer(true)
                    },
                    onDragMove = { rootPointer ->
                        setDragPointer(rootPointer)
                        setHasDragPointer(true)
                    },
                    onEndDrag = { rootPointer ->
                        setDragPointer(rootPointer)
                        setHasDragPointer(true)
                        finishDragAt(rootPointer)
                    },
                    modifier = Modifier
                        .offset { IntOffset(ox, oy) }
                        .width(ww)
                        .height(hh)
                        .zIndex(0.75f)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val info = appWidgetManager.getAppWidgetInfo(w.appWidgetId)
                                val hostView = appWidgetHost.createView(ctx, w.appWidgetId, info)
                                hostView.setAppWidget(w.appWidgetId, info)
                                hostView.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                hostView
                            },
                            update = { hostView ->
                                val info = appWidgetManager.getAppWidgetInfo(w.appWidgetId)
                                hostView.setAppWidget(w.appWidgetId, info)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (editMode) {
                        DeleteBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            onClick = { onDeleteWidget(w.appWidgetId) }
                        )
                    }
                }
            }

            // 3) KÁRTYÁK
            cards.forEach { card ->
                if (card.col != 0) return@forEach
                if (card.row < 0 || card.row + 1 >= rowsToShow) return@forEach

                val ox = cellOffsetX(card.col).roundToInt()
                val oy = cellOffsetY(card.row).roundToInt()

                var cardTopLeftPx by remember(card) { mutableStateOf(Offset.Zero) }
                var lastRootPointer by remember(card) { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(ox, oy) }
                        .width(cardW)
                        .height(cardH)
                        .zIndex(1f)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            cardTopLeftPx = Offset(pos.x, pos.y)
                        }
                ) {
                    // Az eredeti kártya UI
                    PhoneHomeCard(
                        type = card.type,
                        modifier = Modifier.fillMaxSize()
                    )

                    // EDIT MÓDBAN: teljes érintésblokkoló + drag overlay
                    if (editMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(2f)
                                .pointerInput(card) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()

                                        var lastRootPointer = cardTopLeftPx + down.position
                                        var dragStarted = false

                                        val longPress = awaitLongPressOrCancellation(down.id)
                                        if (longPress == null) {
                                            return@awaitEachGesture
                                        }

                                        lastRootPointer = cardTopLeftPx + longPress.position
                                        clearPlaceError()
                                        setDragging(DragPayload.Card(card))
                                        setDragPointer(lastRootPointer)
                                        setHasDragPointer(true)
                                        dragStarted = true

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                            if (!change.pressed) {
                                                if (dragStarted) {
                                                    setDragPointer(lastRootPointer)
                                                    setHasDragPointer(true)
                                                    finishDragAt(lastRootPointer)
                                                }
                                                break
                                            }

                                            lastRootPointer = cardTopLeftPx + change.position
                                            setDragPointer(lastRootPointer)
                                            setHasDragPointer(true)
                                            change.consume()
                                        }
                                    }
                                }
                        )

                        DeleteBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .zIndex(3f),
                            onClick = { onDeleteCard(card) }
                        )
                    }
                }
            }

            // 4) DRAG PREVIEW
            val drag = dragging
            if ((drag is DragPayload.App || drag is DragPayload.Widget) && hasDragPointer) {
                val localX = (dragPointerPx.x - gridTopLeftPx.x).roundToInt()
                val localY = (dragPointerPx.y - gridTopLeftPx.y).roundToInt()

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