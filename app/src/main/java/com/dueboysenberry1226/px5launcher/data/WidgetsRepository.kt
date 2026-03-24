package com.dueboysenberry1226.px5launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val PREF_WIDGETS = stringSetPreferencesKey("widgets_placements_v3")

class WidgetsRepository(private val context: Context) {

    /**
     * Az összes widget placement, minden layoutból.
     */
    val widgetsFlow: Flow<List<WidgetPlacement>> =
        context.px5DataStore.data.map { prefs ->
            val raw = prefs[PREF_WIDGETS] ?: emptySet()
            raw.mapNotNull(::decode).sortedWith(
                compareBy(
                    { it.layoutMode.name },
                    { it.pageIndex },
                    { it.cellY },
                    { it.cellX },
                    { it.appWidgetId }
                )
            )
        }

    /**
     * Csak az adott layouthoz tartozó widgetek.
     */
    fun widgetsFlow(layoutMode: WidgetLayoutMode): Flow<List<WidgetPlacement>> {
        return widgetsFlow.map { list ->
            list.filter { it.layoutMode == layoutMode }
        }
    }

    suspend fun upsert(p: WidgetPlacement) {
        context.px5DataStore.edit { prefs ->
            val cur = (prefs[PREF_WIDGETS] ?: emptySet()).toMutableSet()

            cur.removeAll { s ->
                val id = s.substringBefore("|", missingDelimiterValue = "")
                id.toIntOrNull() == p.appWidgetId
            }

            cur.add(encode(p))
            prefs[PREF_WIDGETS] = cur
        }
    }

    suspend fun remove(appWidgetId: Int) {
        context.px5DataStore.edit { prefs ->
            val cur = (prefs[PREF_WIDGETS] ?: emptySet()).toMutableSet()
            cur.removeAll { s ->
                val id = s.substringBefore("|", missingDelimiterValue = "")
                id.toIntOrNull() == appWidgetId
            }
            prefs[PREF_WIDGETS] = cur
        }
    }

    suspend fun clearAll() {
        context.px5DataStore.edit { prefs ->
            prefs.remove(PREF_WIDGETS)
        }
    }

    // format v3: id|provider|x|y|sx|sy|layoutMode|pageIndex
    // régi v2 is olvasható marad
    private fun encode(p: WidgetPlacement): String {
        return buildString {
            append(p.appWidgetId); append("|")
            append(p.provider.replace("|", "")); append("|")
            append(p.cellX); append("|")
            append(p.cellY); append("|")
            append(p.spanX); append("|")
            append(p.spanY); append("|")
            append(p.layoutMode.name); append("|")
            append(p.pageIndex)
        }
    }

    private fun decode(s: String): WidgetPlacement? {
        val parts = s.split("|")
        if (parts.size < 6) return null

        val id = parts[0].toIntOrNull() ?: return null
        val provider = parts[1]
        val x = parts[2].toIntOrNull() ?: return null
        val y = parts[3].toIntOrNull() ?: return null
        val sx = parts[4].toIntOrNull() ?: return null
        val sy = parts[5].toIntOrNull() ?: return null

        if (sx <= 0 || sy <= 0) return null

        val layoutMode = if (parts.size >= 7) {
            runCatching { WidgetLayoutMode.valueOf(parts[6]) }
                .getOrDefault(WidgetLayoutMode.LANDSCAPE)
        } else {
            WidgetLayoutMode.LANDSCAPE
        }

        val pageIndex = if (parts.size >= 8) {
            parts[7].toIntOrNull()?.coerceAtLeast(0) ?: 0
        } else {
            0
        }

        return WidgetPlacement(
            appWidgetId = id,
            provider = provider,
            cellX = x,
            cellY = y,
            spanX = sx,
            spanY = sy,
            layoutMode = layoutMode,
            pageIndex = pageIndex
        )
    }
}