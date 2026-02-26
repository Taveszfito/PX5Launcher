package com.dueboysenberry1226.px5launcher.data

/**
 * Rács alapú widget elhelyezés.
 *
 * provider: ComponentName.flattenToString() pl. "com.pkg/.MyProvider"
 * cellX/cellY: bal-felső cella
 * spanX/spanY: 1 vagy 2 (kezdetben), de bővíthető.
 */
data class WidgetPlacement(
    val appWidgetId: Int,
    val provider: String,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int
)

/** Egyszerű rács specifikáció. */
data class WidgetGridSpec(
    val cols: Int,
    val rows: Int
)
