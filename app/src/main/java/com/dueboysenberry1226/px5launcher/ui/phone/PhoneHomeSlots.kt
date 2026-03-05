package com.dueboysenberry1226.px5launcher.ui.phone

import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement
import com.dueboysenberry1226.px5launcher.data.PhoneCardType

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
        val d = kotlin.math.abs(r - startRow) + kotlin.math.abs(c - startCol)
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

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