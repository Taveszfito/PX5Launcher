package com.dueboysenberry1226.px5launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val PREF_PINNED = stringSetPreferencesKey("pinned_pkgs")
private val PREF_RECENTS = stringSetPreferencesKey("recents_ordered")

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val iconBitmap: Bitmap?
)

enum class Tab { GAMES, MEDIA, NOTIFICATIONS }

// Top strip elemei: 9 app + 1 all-apps (+ spacerek a fix kijelöléshez)
sealed class TopItem {
    data class App(val app: LaunchableApp) : TopItem()
    data object AllApps : TopItem()
    data object Spacer : TopItem()
}

class LauncherRepository(
    private val context: Context,
    private val pm: PackageManager
) {
    val pinnedFlow: Flow<Set<String>> =
        context.px5DataStore.data.map { it[PREF_PINNED] ?: emptySet() }

    val recentsFlow: Flow<List<String>> =
        context.px5DataStore.data.map { prefs ->
            val raw = prefs[PREF_RECENTS] ?: emptySet()
            raw.mapNotNull { s ->
                val parts = s.split("|", limit = 2)
                if (parts.size == 2) parts[0].toIntOrNull()?.let { idx -> idx to parts[1] } else null
            }.sortedBy { it.first }.map { it.second }
        }

    suspend fun setPinned(pkg: String, value: Boolean) {
        context.px5DataStore.edit { prefs ->
            val cur = (prefs[PREF_PINNED] ?: emptySet()).toMutableSet()
            if (value) cur.add(pkg) else cur.remove(pkg)
            prefs[PREF_PINNED] = cur
        }
    }

    suspend fun pushRecent(pkg: String, maxRecents: Int = 30) {
        context.px5DataStore.edit { prefs ->
            val current = (prefs[PREF_RECENTS] ?: emptySet())
                .mapNotNull { s ->
                    val p = s.split("|", limit = 2)
                    if (p.size == 2) p[0].toIntOrNull()?.let { it to p[1] } else null
                }
                .sortedBy { it.first }
                .map { it.second }
                .toMutableList()

            current.remove(pkg)
            current.add(0, pkg)

            val trimmed = current.take(maxRecents.coerceIn(5, 200))
            val encoded = trimmed.mapIndexed { i, p -> "${i + 1}|$p" }.toSet()
            prefs[PREF_RECENTS] = encoded
        }
    }

    fun loadApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        fun isAppEnabled(pkg: String): Boolean {
            return runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                if (!ai.enabled) return@runCatching false

                val setting = pm.getApplicationEnabledSetting(pkg)
                when (setting) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                    else -> true
                }
            }.getOrDefault(true)
        }

        return resolved
            .mapNotNull { ri ->
                val label = ri.loadLabel(pm).toString().trim()
                val pkg = ri.activityInfo.packageName

                if (pkg.isBlank()) return@mapNotNull null
                if (!isAppEnabled(pkg)) return@mapNotNull null

                val iconBmp = runCatching {
                    ri.loadIcon(pm).toBitmap(width = 128, height = 128, config = Bitmap.Config.ARGB_8888)
                }.getOrNull()

                LaunchableApp(
                    label = label,
                    packageName = pkg,
                    iconBitmap = iconBmp
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun canUninstall(pkg: String): Boolean {
        val ai: ApplicationInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        return (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
    }
}