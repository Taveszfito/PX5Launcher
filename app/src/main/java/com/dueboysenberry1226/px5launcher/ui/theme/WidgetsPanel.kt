@file:Suppress("UnusedBoxWithConstraintsScope")

package com.dueboysenberry1226.px5launcher.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.dueboysenberry1226.px5launcher.data.WidgetLayoutMode
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import kotlin.math.ceil
import kotlin.math.min

private data class PortraitSlot(
    val baseX: Int,
    val baseY: Int
)

private data class LandscapeSlotDef(
    val index: Int,
    val baseX: Int,
    val baseY: Int
)

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

    val isLandscape = layoutMode == WidgetLayoutMode.LANDSCAPE

    val visiblePlacements = remember(placements, layoutMode) {
        placements.filter { it.layoutMode == layoutMode }
    }

    // =========================================================
    // LANDSCAPE: FIX 4 SLOT, TELJESEN ELKÜLÖNÍTETT LOGIKA
    // =========================================================

    // Slot1
    val slot1 = remember { LandscapeSlotDef(index = 0, baseX = 0, baseY = 0) }

    // Slot2
    val slot2 = remember { LandscapeSlotDef(index = 1, baseX = 2, baseY = 0) }

    // Slot3
    val slot3 = remember { LandscapeSlotDef(index = 2, baseX = 4, baseY = 0) }

    // Slot4
    val slot4 = remember { LandscapeSlotDef(index = 3, baseX = 6, baseY = 0) }

    val landscapeSlots = remember {
        listOf(slot1, slot2, slot3, slot4)
    }

    fun placementBelongsToLandscapeSlot(
        p: WidgetPlacement,
        slot: LandscapeSlotDef
    ): Boolean {
        return p.cellX in slot.baseX..(slot.baseX + 1) &&
                p.cellY in slot.baseY..(slot.baseY + 1)
    }

    val slot1Placements = remember(visiblePlacements) {
        visiblePlacements.filter { placementBelongsToLandscapeSlot(it, slot1) }
    }
    val slot2Placements = remember(visiblePlacements) {
        visiblePlacements.filter { placementBelongsToLandscapeSlot(it, slot2) }
    }
    val slot3Placements = remember(visiblePlacements) {
        visiblePlacements.filter { placementBelongsToLandscapeSlot(it, slot3) }
    }
    val slot4Placements = remember(visiblePlacements) {
        visiblePlacements.filter { placementBelongsToLandscapeSlot(it, slot4) }
    }

    fun buildTopLeftMap(list: List<WidgetPlacement>): Map<Pair<Int, Int>, WidgetPlacement> {
        return list.associateBy { it.cellX to it.cellY }
    }

    fun buildCoverMap(list: List<WidgetPlacement>): Map<Pair<Int, Int>, WidgetPlacement> {
        return buildMap {
            for (p in list) {
                for (dy in 0 until p.spanY) {
                    for (dx in 0 until p.spanX) {
                        val x = p.cellX + dx
                        val y = p.cellY + dy
                        if (x in 0 until grid.cols && y in 0 until grid.rows) {
                            put(x to y, p)
                        }
                    }
                }
            }
        }
    }

    val slot1TopLeft = remember(slot1Placements, grid.cols, grid.rows) { buildTopLeftMap(slot1Placements) }
    val slot2TopLeft = remember(slot2Placements, grid.cols, grid.rows) { buildTopLeftMap(slot2Placements) }
    val slot3TopLeft = remember(slot3Placements, grid.cols, grid.rows) { buildTopLeftMap(slot3Placements) }
    val slot4TopLeft = remember(slot4Placements, grid.cols, grid.rows) { buildTopLeftMap(slot4Placements) }

    val slot1Cover = remember(slot1Placements, grid.cols, grid.rows) { buildCoverMap(slot1Placements) }
    val slot2Cover = remember(slot2Placements, grid.cols, grid.rows) { buildCoverMap(slot2Placements) }
    val slot3Cover = remember(slot3Placements, grid.cols, grid.rows) { buildCoverMap(slot3Placements) }
    val slot4Cover = remember(slot4Placements, grid.cols, grid.rows) { buildCoverMap(slot4Placements) }

    fun landscapeSlotIndexForCell(x: Int, y: Int): Int {
        return when {
            x in slot1.baseX..(slot1.baseX + 1) && y in slot1.baseY..(slot1.baseY + 1) -> 0
            x in slot2.baseX..(slot2.baseX + 1) && y in slot2.baseY..(slot2.baseY + 1) -> 1
            x in slot3.baseX..(slot3.baseX + 1) && y in slot3.baseY..(slot3.baseY + 1) -> 2
            x in slot4.baseX..(slot4.baseX + 1) && y in slot4.baseY..(slot4.baseY + 1) -> 3
            else -> 0
        }
    }

    fun landscapeSlotAt(index: Int): LandscapeSlotDef {
        return landscapeSlots[index.coerceIn(0, 3)]
    }

    fun applyLandscapeSlotIndex(idx: Int) {
        val slot = landscapeSlotAt(idx)
        selectedX = slot.baseX
        selectedY = slot.baseY
        clampSel()
    }

    fun landscapeTopLeftMap(slotIndex: Int): Map<Pair<Int, Int>, WidgetPlacement> {
        return when (slotIndex) {
            0 -> slot1TopLeft
            1 -> slot2TopLeft
            2 -> slot3TopLeft
            else -> slot4TopLeft
        }
    }

    fun landscapeCoverMap(slotIndex: Int): Map<Pair<Int, Int>, WidgetPlacement> {
        return when (slotIndex) {
            0 -> slot1Cover
            1 -> slot2Cover
            2 -> slot3Cover
            else -> slot4Cover
        }
    }

    fun landscapePlacements(slotIndex: Int): List<WidgetPlacement> {
        return when (slotIndex) {
            0 -> slot1Placements
            1 -> slot2Placements
            2 -> slot3Placements
            else -> slot4Placements
        }
    }

    fun landscapePlacementAtTopLeft(slotIndex: Int, x: Int, y: Int): WidgetPlacement? {
        return landscapeTopLeftMap(slotIndex)[x to y]
    }

    fun landscapePlacementCovering(slotIndex: Int, x: Int, y: Int): WidgetPlacement? {
        return landscapeCoverMap(slotIndex)[x to y]
    }

    fun landscapeIsCovered(slotIndex: Int, x: Int, y: Int): Boolean {
        return landscapeCoverMap(slotIndex).containsKey(x to y)
    }

    fun landscapeSlotHasAnything(slotIndex: Int): Boolean {
        val slot = landscapeSlotAt(slotIndex)
        val bx = slot.baseX
        val by = slot.baseY
        return landscapeIsCovered(slotIndex, bx, by) ||
                landscapeIsCovered(slotIndex, bx + 1, by) ||
                landscapeIsCovered(slotIndex, bx, by + 1) ||
                landscapeIsCovered(slotIndex, bx + 1, by + 1)
    }

    fun landscapeSlotHasBig2x2Widget(slotIndex: Int): Boolean {
        val slot = landscapeSlotAt(slotIndex)
        val bx = slot.baseX
        val by = slot.baseY
        val p = landscapePlacementCovering(slotIndex, bx, by) ?: return false
        return p.cellX == bx && p.cellY == by && p.spanX >= 2 && p.spanY >= 2
    }

    // =========================================================
    // PORTRAIT / GENERIC
    // =========================================================

    val portraitPlacements = visiblePlacements

    val portraitTopLeft: Map<Pair<Int, Int>, WidgetPlacement> = remember(portraitPlacements, grid.cols, grid.rows) {
        portraitPlacements.associateBy { it.cellX to it.cellY }
    }

    val portraitCoverMap: Map<Pair<Int, Int>, WidgetPlacement> = remember(portraitPlacements, grid.cols, grid.rows) {
        buildMap {
            for (p in portraitPlacements) {
                for (dy in 0 until p.spanY) {
                    for (dx in 0 until p.spanX) {
                        val x = p.cellX + dx
                        val y = p.cellY + dy
                        if (x in 0 until grid.cols && y in 0 until grid.rows) {
                            put(x to y, p)
                        }
                    }
                }
            }
        }
    }

    fun portraitPlacementAtTopLeft(x: Int, y: Int): WidgetPlacement? = portraitTopLeft[x to y]
    fun portraitPlacementCovering(x: Int, y: Int): WidgetPlacement? = portraitCoverMap[x to y]
    fun portraitIsCovered(x: Int, y: Int): Boolean = portraitCoverMap.containsKey(x to y)

    val portraitSlots = remember(grid.cols, grid.rows) {
        val cols = ceil(grid.cols / 2f).toInt().coerceAtLeast(1)
        val rows = ceil(grid.rows / 2f).toInt().coerceAtLeast(1)
        buildList {
            for (sr in 0 until rows) {
                for (sc in 0 until cols) {
                    add(
                        PortraitSlot(
                            baseX = (sc * 2).coerceIn(0, grid.cols - 1),
                            baseY = (sr * 2).coerceIn(0, grid.rows - 1)
                        )
                    )
                }
            }
        }
    }

    fun portraitSlotAt(index: Int): PortraitSlot =
        portraitSlots[index.coerceIn(0, portraitSlots.lastIndex.coerceAtLeast(0))]

    fun portraitSlotIndexForCell(x: Int, y: Int): Int {
        val found = portraitSlots.indexOfFirst { slot ->
            x in slot.baseX..(slot.baseX + 1) && y in slot.baseY..(slot.baseY + 1)
        }
        return if (found >= 0) found else 0
    }

    fun applyPortraitSlotIndex(idx: Int) {
        val slot = portraitSlotAt(idx)
        selectedX = slot.baseX
        selectedY = slot.baseY
        clampSel()
    }

    fun portraitSlotHasAnything(idx: Int): Boolean {
        val slot = portraitSlotAt(idx)
        val bx = slot.baseX
        val by = slot.baseY
        return portraitIsCovered(bx, by) ||
                portraitIsCovered(bx + 1, by) ||
                portraitIsCovered(bx, by + 1) ||
                portraitIsCovered(bx + 1, by + 1)
    }

    fun portraitSlotHasBig2x2Widget(idx: Int): Boolean {
        val slot = portraitSlotAt(idx)
        val bx = slot.baseX
        val by = slot.baseY
        val p = portraitPlacementCovering(bx, by) ?: return false
        return p.cellX == bx && p.cellY == by && p.spanX >= 2 && p.spanY >= 2
    }

    fun portraitSlotIndexForPlacement(p: WidgetPlacement): Int {
        val found = portraitSlots.indexOfFirst { slot ->
            p.cellX in slot.baseX..(slot.baseX + 1) &&
                    p.cellY in slot.baseY..(slot.baseY + 1)
        }
        return if (found >= 0) found else 0
    }

    // =========================================================
    // INPUT
    // =========================================================

    LaunchedEffect(
        Unit,
        grid.cols,
        grid.rows,
        visiblePlacements,
        isLandscape,
        slot1Placements,
        slot2Placements,
        slot3Placements,
        slot4Placements
    ) {
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
                        closeMenu()
                        true
                    }
                    else -> true
                }
            }

            if (isLandscape) {
                val currentSlotIndex = landscapeSlotIndexForCell(selectedX, selectedY)
                val currentSlot = landscapeSlotAt(currentSlotIndex)

                val slotEmpty = !landscapeSlotHasAnything(currentSlotIndex)
                val slotBig = !slotEmpty && landscapeSlotHasBig2x2Widget(currentSlotIndex)

                if (slotEmpty) {
                    return@registerKeyHandler when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (currentSlotIndex > 0) applyLandscapeSlotIndex(currentSlotIndex - 1)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (currentSlotIndex < 3) applyLandscapeSlotIndex(currentSlotIndex + 1)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP -> true
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true

                        in okCodes -> {
                            onRequestAddAt(currentSlot.baseX, currentSlot.baseY)
                            true
                        }

                        in menuCodes -> false
                        else -> false
                    }
                }

                if (slotBig) {
                    return@registerKeyHandler when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (currentSlotIndex > 0) applyLandscapeSlotIndex(currentSlotIndex - 1)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (currentSlotIndex < 3) applyLandscapeSlotIndex(currentSlotIndex + 1)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP -> true
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true

                        in okCodes -> true

                        in menuCodes -> {
                            val p = landscapePlacementCovering(currentSlotIndex, currentSlot.baseX, currentSlot.baseY)
                            if (p != null) {
                                openMenuFor(p)
                                true
                            } else {
                                false
                            }
                        }

                        AndroidKeyEvent.KEYCODE_BACK,
                        AndroidKeyEvent.KEYCODE_ESCAPE,
                        AndroidKeyEvent.KEYCODE_BUTTON_B -> false

                        else -> false
                    }
                }

                val localX = (selectedX - currentSlot.baseX).coerceIn(0, 1)
                val localY = (selectedY - currentSlot.baseY).coerceIn(0, 1)

                fun jumpLandscapeSlot(newIdx: Int, keepLocalX: Int, keepLocalY: Int) {
                    val targetSlot = landscapeSlotAt(newIdx)
                    selectedX = targetSlot.baseX + keepLocalX.coerceIn(0, 1)
                    selectedY = targetSlot.baseY + keepLocalY.coerceIn(0, 1)
                    clampSel()
                }

                return@registerKeyHandler when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (localX > 0) {
                            selectedX -= 1
                            clampSel()
                        } else if (currentSlotIndex > 0) {
                            jumpLandscapeSlot(currentSlotIndex - 1, 1, localY)
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (localX < 1) {
                            selectedX += 1
                            clampSel()
                        } else if (currentSlotIndex < 3) {
                            jumpLandscapeSlot(currentSlotIndex + 1, 0, localY)
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        if (localY > 0) {
                            selectedY -= 1
                            clampSel()
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (localY < 1) {
                            selectedY += 1
                            clampSel()
                        }
                        true
                    }

                    in okCodes -> {
                        val p = landscapePlacementCovering(currentSlotIndex, selectedX, selectedY)
                        if (p != null) {
                            true
                        } else {
                            onRequestAddAt(selectedX, selectedY)
                            true
                        }
                    }

                    in menuCodes -> {
                        val p = landscapePlacementCovering(currentSlotIndex, selectedX, selectedY)
                        if (p != null) {
                            openMenuFor(p)
                            true
                        } else {
                            false
                        }
                    }

                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    AndroidKeyEvent.KEYCODE_BUTTON_B -> false

                    else -> false
                }
            }

            if (code == AndroidKeyEvent.KEYCODE_DPAD_UP && selectedY == 0) {
                return@registerKeyHandler false
            }

            val idx = portraitSlotIndexForCell(selectedX, selectedY)
            val slot = portraitSlotAt(idx)
            val slotEmpty = !portraitSlotHasAnything(idx)
            val slotBig = !slotEmpty && portraitSlotHasBig2x2Widget(idx)
            val slotCount = portraitSlots.size

            if (slotEmpty) {
                return@registerKeyHandler when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (idx > 0) applyPortraitSlotIndex(idx - 1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (idx < slotCount - 1) applyPortraitSlotIndex(idx + 1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_UP -> true
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true

                    in okCodes -> {
                        onRequestAddAt(slot.baseX, slot.baseY)
                        true
                    }

                    in menuCodes -> false
                    else -> false
                }
            }

            if (slotBig) {
                return@registerKeyHandler when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (idx > 0) applyPortraitSlotIndex(idx - 1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (idx < slotCount - 1) applyPortraitSlotIndex(idx + 1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_UP -> true
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true

                    in okCodes -> true

                    in menuCodes -> {
                        val p = portraitPlacementCovering(slot.baseX, slot.baseY)
                        if (p != null) {
                            openMenuFor(p)
                            true
                        } else {
                            false
                        }
                    }

                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_ESCAPE,
                    AndroidKeyEvent.KEYCODE_BUTTON_B -> false

                    else -> false
                }
            }

            val localX = (selectedX - slot.baseX).coerceIn(0, 1)
            val localY = (selectedY - slot.baseY).coerceIn(0, 1)

            fun jumpPortraitSlot(newIdx: Int, keepLocalX: Int, keepLocalY: Int) {
                val target = portraitSlotAt(newIdx)
                selectedX = target.baseX + keepLocalX.coerceIn(0, 1)
                selectedY = target.baseY + keepLocalY.coerceIn(0, 1)
                clampSel()
            }

            return@registerKeyHandler when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (localX > 0) {
                        selectedX -= 1
                        clampSel()
                    } else if (idx > 0) {
                        jumpPortraitSlot(idx - 1, 1, localY)
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (localX < 1) {
                        selectedX += 1
                        clampSel()
                    } else if (idx < slotCount - 1) {
                        jumpPortraitSlot(idx + 1, 0, localY)
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    if (localY > 0) {
                        selectedY -= 1
                        clampSel()
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (localY < 1) {
                        selectedY += 1
                        clampSel()
                    }
                    true
                }

                in okCodes -> {
                    val p = portraitPlacementCovering(selectedX, selectedY)
                    if (p != null) {
                        true
                    } else {
                        onRequestAddAt(selectedX, selectedY)
                        true
                    }
                }

                in menuCodes -> {
                    val p = portraitPlacementCovering(selectedX, selectedY)
                    if (p != null) {
                        openMenuFor(p)
                        true
                    } else {
                        false
                    }
                }

                AndroidKeyEvent.KEYCODE_BACK,
                AndroidKeyEvent.KEYCODE_ESCAPE,
                AndroidKeyEvent.KEYCODE_BUTTON_B -> false

                else -> false
            }
        }
    }

    val noRipple = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier = modifier) {
        val scopeMaxW = maxWidth
        val scopeMaxH = maxHeight
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
                        onLongClick = {}
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
                        onLongClick = {}
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

        @Composable
        fun RenderOneLandscapeSlot(
            slotIndex: Int,
            cardX: Dp,
            cardY: Dp,
            slotSize: Dp,
            innerPad: Dp,
            innerGapPx: Dp,
            smallCell: Dp
        ) {
            val slot = landscapeSlotAt(slotIndex)
            val slotPlacements = landscapePlacements(slotIndex)
            val selectedPlacement = landscapePlacementCovering(
                slotIndex = slotIndex,
                x = selectedX,
                y = selectedY
            )

            fun localOffset(local: Int): Dp = (smallCell + innerGapPx) * local.toFloat()
            fun spanSize(span: Int): Dp = (smallCell * span.toFloat()) + innerGapPx * (span - 1)

            val selectedInside =
                (selectedX in slot.baseX..(slot.baseX + 1)) &&
                        (selectedY in slot.baseY..(slot.baseY + 1))

            val hasAny = landscapeSlotHasAnything(slotIndex)

            if (!hasAny) {
                BigEmptyCard(
                    x = cardX,
                    y = cardY,
                    w = slotSize,
                    h = slotSize,
                    selected = selectedInside,
                    onClick = {
                        selectedX = slot.baseX
                        selectedY = slot.baseY
                        clampSel()
                        onRequestAddAt(slot.baseX, slot.baseY)
                    }
                )
            } else {
                Card(
                    shape = slotShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = if (selectedInside) 0.08f else 0.05f)
                    ),
                    modifier = Modifier
                        .offset(x = cardX, y = cardY)
                        .size(slotSize, slotSize)
                        .border(
                            2.dp,
                            Color.White.copy(alpha = if (selectedInside) 0.35f else 0.18f),
                            slotShape
                        )
                ) {}

                for (cy in 0..1) {
                    for (cx in 0..1) {
                        val gx = slot.baseX + cx
                        val gy = slot.baseY + cy

                        if (landscapeIsCovered(slotIndex, gx, gy)) continue
                        if (landscapePlacementAtTopLeft(slotIndex, gx, gy) != null) continue

                        val isSel = gx == selectedX && gy == selectedY

                        SmallAddCell(
                            x = cardX + innerPad + localOffset(cx),
                            y = cardY + innerPad + localOffset(cy),
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
                }

                for (p in slotPlacements) {
                    key(p.appWidgetId, p.cellX, p.cellY, p.provider) {
                        val localCx = (p.cellX - slot.baseX).coerceIn(0, 1)
                        val localCy = (p.cellY - slot.baseY).coerceIn(0, 1)
                        val isSel = selectedPlacement?.appWidgetId == p.appWidgetId

                        val scale by animateFloatAsState(
                            targetValue = if (isSel) 1.02f else 1.0f,
                            label = "widgetScaleLandscape_$slotIndex"
                        )

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = cardX + innerPad + localOffset(localCx),
                                    y = cardY + innerPad + localOffset(localCy)
                                )
                                .size(spanSize(p.spanX), spanSize(p.spanY))
                                .scale(scale)
                                .clip(cellShape)
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
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
        ) {
            val innerPadOutside = 18.dp
            val availW = (scopeMaxW - innerPadOutside * 2).coerceAtLeast(1.dp)
            val availH = scopeMaxH.coerceAtLeast(1.dp)

            if (isLandscape) {
                val slotCount = 4
                val slotFromW = (availW - outerGap * (slotCount - 1)) / slotCount
                val slotSize = min(slotFromW.value, availH.value).dp

                val totalW = (slotSize * 4f) + outerGap * 3
                val originX = (scopeMaxW - totalW) / 2
                val originY = (scopeMaxH - slotSize) / 2

                val slot1X = originX
                val slot2X = originX + slotSize + outerGap
                val slot3X = originX + (slotSize + outerGap) * 2f
                val slot4X = originX + (slotSize + outerGap) * 3f
                val slotY = originY

                val innerPad = 18.dp
                val smallCell = (slotSize - innerGap - innerPad * 2) / 2
                publishSmallCellPx(smallCell)

                // Slot1
                RenderOneLandscapeSlot(
                    slotIndex = 0,
                    cardX = slot1X,
                    cardY = slotY,
                    slotSize = slotSize,
                    innerPad = innerPad,
                    innerGapPx = innerGap,
                    smallCell = smallCell
                )

                // Slot2
                RenderOneLandscapeSlot(
                    slotIndex = 1,
                    cardX = slot2X,
                    cardY = slotY,
                    slotSize = slotSize,
                    innerPad = innerPad,
                    innerGapPx = innerGap,
                    smallCell = smallCell
                )

                // Slot3
                RenderOneLandscapeSlot(
                    slotIndex = 2,
                    cardX = slot3X,
                    cardY = slotY,
                    slotSize = slotSize,
                    innerPad = innerPad,
                    innerGapPx = innerGap,
                    smallCell = smallCell
                )

                // Slot4
                RenderOneLandscapeSlot(
                    slotIndex = 3,
                    cardX = slot4X,
                    cardY = slotY,
                    slotSize = slotSize,
                    innerPad = innerPad,
                    innerGapPx = innerGap,
                    smallCell = smallCell
                )
            } else {
                val slotCount = portraitSlots.size
                val cols = min(slotCount, 4).coerceAtLeast(1)
                val visibleCount = min(slotCount, cols)

                val slotFromW = (availW - outerGap * (cols - 1)) / cols
                val slotSize = min(slotFromW.value, availH.value).dp

                val gridW = (slotSize * cols.toFloat()) + outerGap * (cols - 1)
                val gridH = slotSize

                val originX = (scopeMaxW - gridW) / 2
                val originY = (scopeMaxH - gridH) / 2

                fun cardX(c: Int): Dp = originX + (slotSize + outerGap) * c.toFloat()
                fun cardY(): Dp = originY

                val innerPad = 18.dp
                val smallCell = (slotSize - innerGap - innerPad * 2) / 2
                publishSmallCellPx(smallCell)

                fun spanSize(span: Int): Dp =
                    (smallCell * span.toFloat()) + innerGap * (span - 1)

                fun localOffset(local: Int): Dp =
                    (smallCell + innerGap) * local.toFloat()

                val selectedPlacement = portraitPlacementCovering(selectedX, selectedY)

                for (i in 0 until visibleCount) {
                    val slotDef = portraitSlotAt(i)
                    val cellX = slotDef.baseX
                    val cellY = slotDef.baseY

                    val selectedInside =
                        (selectedX in cellX..(cellX + 1)) &&
                                (selectedY in cellY..(cellY + 1))

                    val hasAny = portraitSlotHasAnything(i)

                    if (!hasAny) {
                        BigEmptyCard(
                            x = cardX(i),
                            y = cardY(),
                            w = slotSize,
                            h = slotSize,
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
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = if (selectedInside) 0.08f else 0.05f)
                            ),
                            modifier = Modifier
                                .offset(x = cardX(i), y = cardY())
                                .size(slotSize, slotSize)
                                .border(
                                    2.dp,
                                    Color.White.copy(alpha = if (selectedInside) 0.35f else 0.18f),
                                    slotShape
                                )
                        ) {}

                        for (cy in 0..1) {
                            for (cx in 0..1) {
                                val gx = cellX + cx
                                val gy = cellY + cy

                                if (portraitIsCovered(gx, gy)) continue
                                if (portraitPlacementAtTopLeft(gx, gy) != null) continue

                                val isSel = gx == selectedX && gy == selectedY

                                SmallAddCell(
                                    x = cardX(i) + innerPad + localOffset(cx),
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
                        }

                        for (p in portraitPlacements) {
                            val pSlotIndex = portraitSlotIndexForPlacement(p)
                            if (pSlotIndex != i) continue

                            key(p.appWidgetId, p.cellX, p.cellY, p.provider) {
                                val localCx = (p.cellX - cellX).coerceIn(0, 1)
                                val localCy = (p.cellY - cellY).coerceIn(0, 1)

                                val isSel = selectedPlacement?.appWidgetId == p.appWidgetId

                                val scale by animateFloatAsState(
                                    targetValue = if (isSel) 1.02f else 1.0f,
                                    label = "widgetScalePortrait"
                                )

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = cardX(i) + innerPad + localOffset(localCx),
                                            y = cardY() + innerPad + localOffset(localCy)
                                        )
                                        .size(spanSize(p.spanX), spanSize(p.spanY))
                                        .scale(scale)
                                        .clip(cellShape)
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
                    onClick = {
                        target?.let(onMove)
                        closeMenu()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.widgets_delete),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = {
                        target?.let(onDelete)
                        closeMenu()
                    }
                )
            }
        }
    }
}