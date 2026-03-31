@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import com.dueboysenberry1226.px5launcher.data.WidgetGridSpec
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import kotlin.math.abs

internal fun canPlaceAtWidget(
    gridSpec: WidgetGridSpec,
    placements: List<WidgetPlacement>,
    cellX: Int,
    cellY: Int,
    spanX: Int,
    spanY: Int,
    ignoreAppWidgetId: Int? = null
): Boolean {
    if (cellX < 0 || cellY < 0) return false
    if (cellX + spanX > gridSpec.cols) return false
    if (cellY + spanY > gridSpec.rows) return false

    val occ = Array(gridSpec.rows) { BooleanArray(gridSpec.cols) }

    for (p in placements) {
        if (ignoreAppWidgetId != null && p.appWidgetId == ignoreAppWidgetId) continue

        for (dy in 0 until p.spanY) {
            for (dx in 0 until p.spanX) {
                val x = p.cellX + dx
                val y = p.cellY + dy
                if (y in 0 until gridSpec.rows && x in 0 until gridSpec.cols) {
                    occ[y][x] = true
                }
            }
        }
    }

    for (dy in 0 until spanY) {
        for (dx in 0 until spanX) {
            val x = cellX + dx
            val y = cellY + dy
            if (occ[y][x]) return false
        }
    }

    return true
}

internal fun resolveAnchorForTap(
    gridSpec: WidgetGridSpec,
    placements: List<WidgetPlacement>,
    tapX: Int,
    tapY: Int,
    spanX: Int,
    spanY: Int,
    ignoreAppWidgetId: Int? = null
): Pair<Int, Int>? {
    val candidates = mutableListOf<Pair<Int, Int>>()

    val minX = (tapX - (spanX - 1)).coerceAtLeast(0)
    val maxX = tapX.coerceAtMost(gridSpec.cols - spanX)

    val minY = (tapY - (spanY - 1)).coerceAtLeast(0)
    val maxY = tapY.coerceAtMost(gridSpec.rows - spanY)

    for (y in minY..maxY) {
        for (x in minX..maxX) {
            candidates += x to y
        }
    }

    val sorted = candidates.sortedBy { (x, y) ->
        abs(x - tapX) + abs(y - tapY)
    }

    return sorted.firstOrNull { (x, y) ->
        canPlaceAtWidget(
            gridSpec = gridSpec,
            placements = placements,
            cellX = x,
            cellY = y,
            spanX = spanX,
            spanY = spanY,
            ignoreAppWidgetId = ignoreAppWidgetId
        )
    }
}

internal fun nextClockwiseSlotWidget(
    gridSpec: WidgetGridSpec,
    placements: List<WidgetPlacement>,
    spanX: Int,
    spanY: Int,
    startX: Int,
    startY: Int,
    ignoreAppWidgetId: Int? = null
): Pair<Int, Int>? {
    fun slotBaseXForCell(x: Int): Int {
        return ((x / 2) * 2).coerceIn(0, gridSpec.cols - 2)
    }

    val slotBaseX = slotBaseXForCell(startX)

    if (spanX == 2 && spanY == 1) {
        val candidates = listOf(
            slotBaseX to 0,
            slotBaseX to 1
        )

        val current = startX to startY
        val startIdx = candidates.indexOf(current).takeIf { it >= 0 } ?: 0

        for (step in 1..candidates.size) {
            val (x, y) = candidates[(startIdx + step) % candidates.size]
            if (
                canPlaceAtWidget(
                    gridSpec = gridSpec,
                    placements = placements,
                    cellX = x,
                    cellY = y,
                    spanX = spanX,
                    spanY = spanY,
                    ignoreAppWidgetId = ignoreAppWidgetId
                )
            ) {
                return x to y
            }
        }
        return null
    }

    if (spanX == 1 && spanY == 2) {
        val candidates = listOf(
            slotBaseX to 0,
            (slotBaseX + 1) to 0
        )

        val current = startX to startY
        val startIdx = candidates.indexOf(current).takeIf { it >= 0 } ?: 0

        for (step in 1..candidates.size) {
            val (x, y) = candidates[(startIdx + step) % candidates.size]
            if (
                canPlaceAtWidget(
                    gridSpec = gridSpec,
                    placements = placements,
                    cellX = x,
                    cellY = y,
                    spanX = spanX,
                    spanY = spanY,
                    ignoreAppWidgetId = ignoreAppWidgetId
                )
            ) {
                return x to y
            }
        }
        return null
    }

    val total = gridSpec.cols * gridSpec.rows
    val startIdx = (startY * gridSpec.cols + startX).coerceIn(0, total - 1)

    for (step in 1..total) {
        val idx = (startIdx + step) % total
        val x = idx % gridSpec.cols
        val y = idx / gridSpec.cols

        if (
            canPlaceAtWidget(
                gridSpec = gridSpec,
                placements = placements,
                cellX = x,
                cellY = y,
                spanX = spanX,
                spanY = spanY,
                ignoreAppWidgetId = ignoreAppWidgetId
            )
        ) {
            return x to y
        }
    }

    return null
}