package com.dueboysenberry1226.px5launcher.ui.phone

import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import kotlin.math.abs

internal const val CARD_SPAN_X = 4
internal const val CARD_SPAN_Y = 2

internal fun normalizeSlots(list: List<String?>): List<String?> {
    val out = ArrayList<String?>(SLOTS)
    for (i in 0 until SLOTS) out += list.getOrNull(i)?.trim().takeUnless { it.isNullOrBlank() }
    return out
}

internal fun nearestFreeSlot(
    slots: List<String?>,
    startIdx: Int,
    visibleSlots: Int
): Int? {
    if (startIdx !in 0 until visibleSlots) return null
    if (slots.getOrNull(startIdx) == null) return startIdx

    val startRow = startIdx / COLS
    val startCol = startIdx % COLS

    var best: Int? = null
    var bestDist = Int.MAX_VALUE

    for (i in 0 until visibleSlots) {
        if (slots.getOrNull(i) != null) continue
        val r = i / COLS
        val c = i % COLS
        val d = abs(r - startRow) + abs(c - startCol)
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

// ---------------------------
// ✅ Widget area helpers
// ---------------------------

private fun buildOccupiedMap(
    slots: List<String?>,
    cards: List<PhoneCardPlacement>,
    widgets: List<WidgetPlacement>,
    rowsToShow: Int,
    ignoreWidgetId: Int? = null
): BooleanArray {
    val visibleSlots = rowsToShow * COLS
    val occ = BooleanArray(visibleSlots) { false }

    // apps
    for (i in 0 until visibleSlots) {
        if (slots.getOrNull(i) != null) occ[i] = true
    }

    // cards (4x2, col=0)
    cards.forEach { c ->
        if (c.col != 0) return@forEach
        for (dy in 0 until CARD_SPAN_Y) {
            for (dx in 0 until CARD_SPAN_X) {
                val rr = c.row + dy
                val cc = c.col + dx
                if (rr in 0 until rowsToShow && cc in 0 until COLS) {
                    val idx = rr * COLS + cc
                    if (idx in 0 until visibleSlots) occ[idx] = true
                }
            }
        }
    }

    // widgets
    widgets.forEach { w ->
        if (ignoreWidgetId != null && w.appWidgetId == ignoreWidgetId) return@forEach
        for (dy in 0 until w.spanY) {
            for (dx in 0 until w.spanX) {
                val rr = w.cellY + dy
                val cc = w.cellX + dx
                if (rr in 0 until rowsToShow && cc in 0 until COLS) {
                    val idx = rr * COLS + cc
                    if (idx in 0 until visibleSlots) occ[idx] = true
                }
            }
        }
    }

    return occ
}

internal fun isAreaFreeForWidget(
    slots: List<String?>,
    cards: List<PhoneCardPlacement>,
    widgets: List<WidgetPlacement>,
    cellX: Int,
    cellY: Int,
    spanX: Int,
    spanY: Int,
    rowsToShow: Int,
    ignoreWidgetId: Int? = null
): Boolean {
    if (spanX <= 0 || spanY <= 0) return false
    if (cellX < 0 || cellY < 0) return false
    if (cellX + spanX > COLS) return false
    if (cellY + spanY > rowsToShow) return false

    val occ = buildOccupiedMap(slots, cards, widgets, rowsToShow, ignoreWidgetId)
    val visibleSlots = rowsToShow * COLS

    for (dy in 0 until spanY) {
        for (dx in 0 until spanX) {
            val idx = (cellY + dy) * COLS + (cellX + dx)
            if (idx !in 0 until visibleSlots) return false
            if (occ[idx]) return false
        }
    }
    return true
}

internal fun nearestFreeWidgetCell(
    slots: List<String?>,
    cards: List<PhoneCardPlacement>,
    widgets: List<WidgetPlacement>,
    startCellX: Int,
    startCellY: Int,
    spanX: Int,
    spanY: Int,
    rowsToShow: Int,
    ignoreWidgetId: Int? = null
): Pair<Int, Int>? {
    val maxX = COLS - spanX
    val maxY = rowsToShow - spanY
    if (maxX < 0 || maxY < 0) return null

    val sx = startCellX.coerceIn(0, maxX)
    val sy = startCellY.coerceIn(0, maxY)

    if (isAreaFreeForWidget(slots, cards, widgets, sx, sy, spanX, spanY, rowsToShow, ignoreWidgetId)) {
        return sx to sy
    }

    var best: Pair<Int, Int>? = null
    var bestDist = Int.MAX_VALUE

    for (y in 0..maxY) {
        for (x in 0..maxX) {
            if (!isAreaFreeForWidget(slots, cards, widgets, x, y, spanX, spanY, rowsToShow, ignoreWidgetId)) continue
            val d = abs(x - sx) + abs(y - sy)
            if (d < bestDist) {
                bestDist = d
                best = x to y
            }
        }
    }
    return best
}

// ---------------------------
// Existing card helpers (unchanged)
// ---------------------------

internal fun isAreaFreeForCard(
    slots: List<String?>,
    cards: List<PhoneCardPlacement>,
    targetRow: Int,
    rowsToShow: Int,
    ignore: PhoneCardPlacement? = null
): Boolean {
    if (targetRow < 0) return false
    if (targetRow + CARD_SPAN_Y > rowsToShow) return false

    val visibleSlots = rowsToShow * COLS
    val occ = BooleanArray(visibleSlots) { false }

    for (i in 0 until visibleSlots) {
        if (slots.getOrNull(i) != null) occ[i] = true
    }

    cards.forEach { c ->
        if (ignore != null && c == ignore) return@forEach
        if (c.col != 0) return@forEach
        for (dy in 0 until CARD_SPAN_Y) {
            for (dx in 0 until CARD_SPAN_X) {
                val rr = c.row + dy
                val cc = c.col + dx
                if (rr in 0 until rowsToShow && cc in 0 until COLS) {
                    val idx = rr * COLS + cc
                    if (idx in 0 until visibleSlots) occ[idx] = true
                }
            }
        }
    }

    for (dy in 0 until CARD_SPAN_Y) {
        for (dx in 0 until CARD_SPAN_X) {
            val idx = (targetRow + dy) * COLS + dx
            if (idx !in 0 until visibleSlots) return false
            if (occ[idx]) return false
        }
    }
    return true
}

internal fun nearestFreeCardRow(
    slots: List<String?>,
    cards: List<PhoneCardPlacement>,
    targetRow: Int,
    rowsToShow: Int,
    ignore: PhoneCardPlacement
): Int? {
    val minRow = 0
    val maxRow = rowsToShow - CARD_SPAN_Y
    val clamped = targetRow.coerceIn(minRow, maxRow)

    if (isAreaFreeForCard(slots, cards, clamped, rowsToShow, ignore)) return clamped

    for (delta in 1..maxRow) {
        val up = clamped - delta
        val down = clamped + delta
        if (up >= minRow && isAreaFreeForCard(slots, cards, up, rowsToShow, ignore)) return up
        if (down <= maxRow && isAreaFreeForCard(slots, cards, down, rowsToShow, ignore)) return down
    }
    return null
}

internal fun tryPlaceCard(
    slots: List<String?>,
    cards: MutableList<PhoneCardPlacement>,
    type: PhoneCardType,
    rowsToShow: Int
): Boolean {
    val visibleSlots = rowsToShow * COLS
    if (rowsToShow < CARD_SPAN_Y) return false

    val occ = BooleanArray(visibleSlots) { false }

    for (i in 0 until visibleSlots) {
        if (slots.getOrNull(i) != null) occ[i] = true
    }

    fun markCard(r: Int, c: Int) {
        for (dy in 0 until CARD_SPAN_Y) {
            for (dx in 0 until CARD_SPAN_X) {
                val rr = r + dy
                val cc = c + dx
                if (rr in 0 until rowsToShow && cc in 0 until COLS) {
                    val idx = rr * COLS + cc
                    if (idx in 0 until visibleSlots) occ[idx] = true
                }
            }
        }
    }
    cards.forEach { markCard(it.row, it.col) }

    for (r in 0..(rowsToShow - CARD_SPAN_Y)) {
        val c = 0
        var ok = true
        for (dy in 0 until CARD_SPAN_Y) {
            for (dx in 0 until CARD_SPAN_X) {
                val idx = (r + dy) * COLS + (c + dx)
                if (idx !in 0 until visibleSlots || occ[idx]) {
                    ok = false
                    break
                }
            }
            if (!ok) break
        }
        if (ok) {
            cards.add(PhoneCardPlacement(type, r, c))
            return true
        }
    }
    return false
}