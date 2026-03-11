package com.dueboysenberry1226.px5launcher.ui

import com.dueboysenberry1226.px5launcher.data.WidgetLayoutMode
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.WidgetGridSpec
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import kotlin.math.ceil
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetsPanel(
    placements: List<WidgetPlacement>,
    grid: WidgetGridSpec,
    layoutMode: WidgetLayoutMode,
    focusRequester: FocusRequester,
    registerKeyHandler: (handler: (KeyEvent) -> Boolean) -> Unit,
    renderWidget: @Composable (placement: WidgetPlacement, modifier: Modifier) -> Unit,
    onRequestAddAt: (cellX: Int, cellY: Int) -> Unit,
    onMove: (placement: WidgetPlacement) -> Unit,
    onDelete: (placement: WidgetPlacement) -> Unit,
    onCellPxKnown: (cellPx: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedX by remember { mutableIntStateOf(0) }
    var selectedY by remember { mutableIntStateOf(0) }

    var menuOpen by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<WidgetPlacement?>(null) }

    fun openMenuFor(p: WidgetPlacement) {
        menuTarget = p
        menuOpen = true
    }

    fun closeMenu() {
        menuOpen = false
        menuTarget = null
    }

    fun clampSel() {
        selectedX = selectedX.coerceIn(0, grid.cols - 1)
        selectedY = selectedY.coerceIn(0, grid.rows - 1)
    }

    val slotColsLogical = ceil(grid.cols / 2f).toInt().coerceAtLeast(1)
    val slotRowsLogical = ceil(grid.rows / 2f).toInt().coerceAtLeast(1)
    val slotCount = slotColsLogical * slotRowsLogical

    var visualCols by remember { mutableIntStateOf(slotColsLogical.coerceAtLeast(1)) }
    val visiblePlacements = remember(placements, layoutMode) {
        placements.filter { it.layoutMode == layoutMode }
    }

    val topLeft: Map<Pair<Int, Int>, WidgetPlacement> = remember(visiblePlacements, grid.cols, grid.rows) {
        visiblePlacements.associateBy { it.cellX to it.cellY }
    }

    val coverMap: Map<Pair<Int, Int>, WidgetPlacement> = remember(visiblePlacements, grid.cols, grid.rows) {
        buildMap {
            for (p in placements) {
                for (dy in 0 until p.spanY) for (dx in 0 until p.spanX) {
                    val x = p.cellX + dx
                    val y = p.cellY + dy
                    if (x in 0 until grid.cols && y in 0 until grid.rows) {
                        put(x to y, p)
                    }
                }
            }
        }
    }

    fun placementAtTopLeft(x: Int, y: Int): WidgetPlacement? = topLeft[x to y]
    fun placementCovering(x: Int, y: Int): WidgetPlacement? = coverMap[x to y]
    fun isCovered(x: Int, y: Int): Boolean = coverMap.containsKey(x to y)

    fun slotTopLeftCellX(sc: Int): Int = (sc * 2).coerceIn(0, grid.cols - 1)
    fun slotTopLeftCellY(sr: Int): Int = (sr * 2).coerceIn(0, grid.rows - 1)

    fun slotHasAnything(sc: Int, sr: Int): Boolean {
        val bx = slotTopLeftCellX(sc)
        val by = slotTopLeftCellY(sr)
        return isCovered(bx, by) ||
                isCovered(bx + 1, by) ||
                isCovered(bx, by + 1) ||
                isCovered(bx + 1, by + 1)
    }

    fun selectedSlotCol(): Int = (selectedX / 2).coerceIn(0, slotColsLogical - 1)
    fun selectedSlotRow(): Int = (selectedY / 2).coerceIn(0, slotRowsLogical - 1)
    fun selectedSlotIndex(): Int = selectedSlotRow() * slotColsLogical + selectedSlotCol()

    fun applySlotIndex(idx: Int) {
        val i = idx.coerceIn(0, slotCount - 1)
        val sr = i / slotColsLogical
        val sc = i % slotColsLogical
        selectedX = slotTopLeftCellX(sc)
        selectedY = slotTopLeftCellY(sr)
        clampSel()
    }

    fun slotHasBig2x2Widget(sc: Int, sr: Int): Boolean {
        val bx = slotTopLeftCellX(sc)
        val by = slotTopLeftCellY(sr)
        val p = placementCovering(bx, by) ?: return false
        return p.cellX == bx && p.cellY == by && p.spanX >= 2 && p.spanY >= 2
    }

    LaunchedEffect(Unit, grid.cols, grid.rows, visiblePlacements, visualCols, slotCount) {
        registerKeyHandler { e ->
            val nk = e.nativeKeyEvent
            val code = nk.keyCode
            val action = nk.action

            val okCodes = setOf(
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_BUTTON_A
            )

            val menuCodes = setOf(
                AndroidKeyEvent.KEYCODE_MENU,
                AndroidKeyEvent.KEYCODE_BUTTON_START,
                AndroidKeyEvent.KEYCODE_BUTTON_SELECT
            )

            if (action != AndroidKeyEvent.ACTION_DOWN) return@registerKeyHandler false

            if (menuOpen) {
                return@registerKeyHandler when (code) {
                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                        closeMenu(); true
                    }
                    else -> true
                }
            }

            if (code == AndroidKeyEvent.KEYCODE_DPAD_UP && selectedY == 0) {
                return@registerKeyHandler false
            }

            val sc = selectedSlotCol()
            val sr = selectedSlotRow()

            val slotEmpty = !slotHasAnything(sc, sr)
            val slotBig = !slotEmpty && slotHasBig2x2Widget(sc, sr)

            val colsNow = visualCols.coerceAtLeast(1)
            val rowsNow = ceil(slotCount / colsNow.toFloat()).toInt().coerceAtLeast(1)
            val idx = selectedSlotIndex()

            if (slotEmpty) {
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        val c = idx % colsNow
                        if (c > 0) applySlotIndex(idx - 1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val c = idx % colsNow
                        if (c < colsNow - 1 && idx + 1 < slotCount) applySlotIndex(idx + 1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        val up = idx - colsNow
                        if (up >= 0) applySlotIndex(up)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (rowsNow > 1) {
                            val down = idx + colsNow
                            if (down < slotCount) applySlotIndex(down)
                        }
                        true
                    }

                    in okCodes -> {
                        onRequestAddAt(slotTopLeftCellX(sc), slotTopLeftCellY(sr))
                        true
                    }

                    in menuCodes -> false
                    else -> false
                }
            } else {
                if (slotBig) {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            val c = idx % colsNow
                            if (c > 0) applySlotIndex(idx - 1)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val c = idx % colsNow
                            if (c < colsNow - 1 && idx + 1 < slotCount) applySlotIndex(idx + 1)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            val up = idx - colsNow
                            if (up >= 0) applySlotIndex(up)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (rowsNow > 1) {
                                val down = idx + colsNow
                                if (down < slotCount) applySlotIndex(down)
                            }
                            true
                        }

                        in okCodes -> true

                        in menuCodes -> {
                            val bx = slotTopLeftCellX(sc)
                            val by = slotTopLeftCellY(sr)
                            val p = placementCovering(bx, by)
                            if (p != null) { openMenuFor(p); true } else false
                        }

                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE,
                        AndroidKeyEvent.KEYCODE_BUTTON_B -> false

                        else -> false
                    }
                } else {
                    val baseX = slotTopLeftCellX(sc)
                    val baseY = slotTopLeftCellY(sr)

                    val localX = (selectedX - baseX).coerceIn(0, 1)
                    val localY = (selectedY - baseY).coerceIn(0, 1)

                    fun jumpToSlot(newIdx: Int, keepLocalX: Int, keepLocalY: Int) {
                        val i2 = newIdx.coerceIn(0, slotCount - 1)
                        val sr2 = i2 / slotColsLogical
                        val sc2 = i2 % slotColsLogical
                        selectedX = slotTopLeftCellX(sc2) + keepLocalX.coerceIn(0, 1)
                        selectedY = slotTopLeftCellY(sr2) + keepLocalY.coerceIn(0, 1)
                        clampSel()
                    }

                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (localX > 0) {
                                selectedX -= 1
                                clampSel()
                            } else {
                                val c = idx % colsNow
                                if (c > 0) jumpToSlot(idx - 1, 1, localY)
                            }
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (localX < 1) {
                                selectedX += 1
                                clampSel()
                            } else {
                                val c = idx % colsNow
                                if (c < colsNow - 1 && idx + 1 < slotCount) jumpToSlot(idx + 1, 0, localY)
                            }
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (localY > 0) {
                                selectedY -= 1
                                clampSel()
                            } else {
                                val up = idx - colsNow
                                if (up >= 0) jumpToSlot(up, localX, 1)
                            }
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (localY < 1) {
                                selectedY += 1
                                clampSel()
                            } else {
                                if (rowsNow > 1) {
                                    val down = idx + colsNow
                                    if (down < slotCount) jumpToSlot(down, localX, 0)
                                }
                            }
                            true
                        }

                        in okCodes -> {
                            val p = placementCovering(selectedX, selectedY)
                            if (p != null) true
                            else {
                                onRequestAddAt(selectedX, selectedY)
                                true
                            }
                        }

                        in menuCodes -> {
                            val p = placementCovering(selectedX, selectedY)
                            if (p != null) { openMenuFor(p); true } else false
                        }

                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE,
                        AndroidKeyEvent.KEYCODE_BUTTON_B -> false

                        else -> false
                    }
                }
            }
        }
    }

    val noRipple = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier = modifier) {
        val scopeMaxW: Dp = this.maxWidth
        val scopeMaxH: Dp = this.maxHeight
        val density = LocalDensity.current

        val outerGap = 18.dp
        val innerGap = 14.dp

        val slotShape = RoundedCornerShape(22.dp)
        val cellShape = RoundedCornerShape(18.dp)

        fun publishSmallCellPx(smallCell: Dp) {
            val px = with(density) { smallCell.toPx() }
            if (px > 0f) onCellPxKnown(px)
        }

        @Composable
        fun BigEmptyCard(
            x: Dp,
            y: Dp,
            w: Dp,
            h: Dp,
            selected: Boolean,
            onClick: () -> Unit
        ) {
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.03f else 1.0f,
                label = "bigEmptyScale"
            )
            val bgAlpha = if (selected) 0.12f else 0.07f
            val borderAlpha = if (selected) 0.95f else 0.18f

            Card(
                shape = slotShape,
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = bgAlpha)),
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(w, h)
                    .scale(scale)
                    .border(2.dp, Color.White.copy(alpha = borderAlpha), slotShape)
                    .combinedClickable(
                        interactionSource = remember { noRipple },
                        indication = null,
                        onClick = onClick,
                        onLongClick = { }
                    )
            ) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.widgets_add),
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(18.dp, 16.dp)
                    )
                    Text(
                        text = "+",
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        @Composable
        fun SmallAddCell(
            x: Dp,
            y: Dp,
            size: Dp,
            selected: Boolean,
            onClick: () -> Unit
        ) {
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.06f else 1.0f,
                label = "smallPlusScale"
            )
            val bgAlpha = if (selected) 0.12f else 0.06f

            Card(
                shape = cellShape,
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = bgAlpha)),
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(size)
                    .scale(scale)
                    .border(2.dp, Color.White.copy(alpha = if (selected) 0.95f else 0.18f), cellShape)
                    .combinedClickable(
                        interactionSource = remember { noRipple },
                        indication = null,
                        onClick = onClick,
                        onLongClick = { }
                    )
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        color = Color.White.copy(alpha = 0.82f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
        ) {
            val innerPadOutside = 18.dp
            val availW: Dp = (scopeMaxW - innerPadOutside * 2).coerceAtLeast(1.dp)
            val availH: Dp = scopeMaxH.coerceAtLeast(1.dp)

            val cols = min(slotCount, 4).coerceAtLeast(1)
            val visibleCount = min(slotCount, cols)

            SideEffect { visualCols = cols }

            val slotFromW: Dp = (availW - outerGap * (cols - 1)) / cols
            val slot: Dp = min(slotFromW.value, availH.value).dp

            val gridW: Dp = (slot * cols.toFloat()) + outerGap * (cols - 1)
            val gridH: Dp = slot

            val originX: Dp = (scopeMaxW - gridW) / 2
            val originY: Dp = (scopeMaxH - gridH) / 2

            fun cardX(c: Int): Dp = originX + (slot + outerGap) * c.toFloat()
            fun cardY(): Dp = originY

            val innerPad = 18.dp
            val smallCell: Dp = (slot - innerGap - innerPad * 2) / 2
            publishSmallCellPx(smallCell)

            fun spanSize(span: Int): Dp =
                (smallCell * span.toFloat()) + innerGap * (span - 1)

            fun localOffset(local: Int): Dp =
                (smallCell + innerGap) * local.toFloat()

            val selectedPlacement = placementCovering(selectedX, selectedY)

            for (i in 0 until visibleCount) {
                val c = i

                val logicalSlotY = i / slotColsLogical
                val logicalSlotX = i % slotColsLogical

                val sc = logicalSlotX
                val sr = logicalSlotY

                val cellX = slotTopLeftCellX(sc)
                val cellY = slotTopLeftCellY(sr)

                val selectedInside =
                    (selectedX in cellX..(cellX + 1)) && (selectedY in cellY..(cellY + 1))

                val hasAny = slotHasAnything(sc, sr)

                if (!hasAny) {
                    BigEmptyCard(
                        x = cardX(c),
                        y = cardY(),
                        w = slot,
                        h = slot,
                        selected = selectedInside,
                        onClick = {
                            selectedX = cellX
                            selectedY = cellY
                            clampSel()
                            onRequestAddAt(cellX, cellY)
                        }
                    )
                } else {
                    Card(
                        shape = slotShape,
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = if (selectedInside) 0.08f else 0.05f)),
                        modifier = Modifier
                            .offset(x = cardX(c), y = cardY())
                            .size(slot, slot)
                            .border(
                                2.dp,
                                Color.White.copy(alpha = if (selectedInside) 0.35f else 0.18f),
                                slotShape
                            )
                    ) {}

                    for (cy in 0..1) for (cx in 0..1) {
                        val gx = cellX + cx
                        val gy = cellY + cy

                        if (isCovered(gx, gy)) continue
                        if (placementAtTopLeft(gx, gy) != null) continue

                        val isSel = (gx == selectedX && gy == selectedY)

                        SmallAddCell(
                            x = cardX(c) + innerPad + localOffset(cx),
                            y = cardY() + innerPad + localOffset(cy),
                            size = smallCell,
                            selected = isSel,
                            onClick = {
                                selectedX = gx
                                selectedY = gy
                                clampSel()
                                onRequestAddAt(gx, gy)
                            }
                        )
                    }

                    for (p in visiblePlacements) {
                        val pSc = (p.cellX / 2).coerceIn(0, slotColsLogical - 1)
                        val pSr = (p.cellY / 2).coerceIn(0, slotRowsLogical - 1)
                        if (pSc != sc || pSr != sr) continue

                        val localCx = (p.cellX - cellX).coerceIn(0, 1)
                        val localCy = (p.cellY - cellY).coerceIn(0, 1)

                        val isSel = (selectedPlacement?.appWidgetId == p.appWidgetId)

                        val scale by animateFloatAsState(
                            targetValue = if (isSel) 1.02f else 1.0f,
                            label = "widgetScale"
                        )

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = cardX(c) + innerPad + localOffset(localCx),
                                    y = cardY() + innerPad + localOffset(localCy)
                                )
                                .size(spanSize(p.spanX), spanSize(p.spanY))
                                .scale(scale)
                                .border(
                                    2.dp,
                                    Color.White.copy(alpha = if (isSel) 0.95f else 0.18f),
                                    cellShape
                                )
                                .combinedClickable(
                                    interactionSource = remember { noRipple },
                                    indication = null,
                                    onClick = {
                                        selectedX = p.cellX
                                        selectedY = p.cellY
                                        clampSel()
                                    },
                                    onLongClick = { openMenuFor(p) }
                                )
                        ) {
                            renderWidget(p, Modifier.fillMaxSize())
                        }
                    }
                }
            }

            val target = menuTarget
            DropdownMenu(
                expanded = menuOpen && target != null,
                onDismissRequest = { closeMenu() },
                modifier = Modifier.background(Color(0xFF111827))
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.widgets_move),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = { target?.let(onMove); closeMenu() }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.widgets_delete),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = { target?.let(onDelete); closeMenu() }
                )
            }
        }
    }
}