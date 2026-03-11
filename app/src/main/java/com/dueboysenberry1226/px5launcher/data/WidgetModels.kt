package com.dueboysenberry1226.px5launcher.data

/**
 * Melyik elrendezéshez tartozik a widget.
 */
enum class WidgetLayoutMode {
    LANDSCAPE,
    PORTRAIT;

    companion object {
        fun fromGrid(grid: WidgetGridSpec): WidgetLayoutMode {
            return if (grid.rows > grid.cols) PORTRAIT else LANDSCAPE
        }
    }
}

/**
 * Rács alapú widget elhelyezés.
 *
 * provider: ComponentName.flattenToString() pl. "com.pkg/.MyProvider"
 * cellX/cellY: bal-felső cella a rácson
 * spanX/spanY: widget méret cellákban
 * layoutMode: melyik orientációs layoutba lett lerakva
 */
data class WidgetPlacement(
    val appWidgetId: Int,
    val provider: String,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val layoutMode: WidgetLayoutMode = WidgetLayoutMode.LANDSCAPE
)

/** Egyszerű rács specifikáció. */
data class WidgetGridSpec(
    val cols: Int,
    val rows: Int
)