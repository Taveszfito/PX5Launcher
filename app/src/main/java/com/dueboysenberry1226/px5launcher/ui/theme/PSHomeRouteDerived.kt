@file:OptIn(ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")

package com.dueboysenberry1226.px5launcher.ui.theme

import com.dueboysenberry1226.px5launcher.R
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import com.dueboysenberry1226.px5launcher.data.LaunchableApp
import com.dueboysenberry1226.px5launcher.data.LauncherRepository
import com.dueboysenberry1226.px5launcher.data.NotificationsRepository
import com.dueboysenberry1226.px5launcher.data.QuickSettingsRepository
import com.dueboysenberry1226.px5launcher.data.Tab
import com.dueboysenberry1226.px5launcher.data.TopItem
import com.dueboysenberry1226.px5launcher.data.WidgetLayoutMode
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import com.dueboysenberry1226.px5launcher.data.WidgetsRepository
import com.dueboysenberry1226.px5launcher.util.computeDominantColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PSHomeDerivedData(
    val repo: LauncherRepository,
    val widgetsRepo: WidgetsRepository,
    val allApps: List<LaunchableApp>,
    val pinned: Set<String>,
    val recents: List<String>,
    val placements: List<WidgetPlacement>,
    val liveNotifs: List<Any>,
    val historyNotifs: List<Any>,
    val qsState: Any,
    val allAppsGrid: List<AllAppsGridItem>,
    val allAppsLastIndex: Int,
    val topApps: List<LaunchableApp>,
    val anchorIndex: Int,
    val realItems: List<TopItem>,
    val realLastIndex: Int,
    val topItems: List<TopItem>,
    val stripState: LazyListState,
    val fling: FlingBehavior,
    val columns: Int,
    val tabOrder: List<Tab>,
    val okCodes: Set<Int>,
    val bg: Brush,
    val homeScrollState: LazyListState,
    val bumpAppsRefreshTick: () -> Unit,
    val selectedTopItem: (Int, HomeSection) -> TopItem?,
    val selectedApp: (Int, HomeSection) -> LaunchableApp?
)

@Composable
internal fun rememberPSHomeDerivedData(
    context: Context,
    pm: PackageManager,
    accentFromIcon: Boolean,
    allAppsColumns: Int
): PSHomeDerivedData {
    val repo = remember(pm, context) { LauncherRepository(context, pm) }
    val widgetsRepo = remember(context) { WidgetsRepository(context) }

    LaunchedEffect(Unit) {
        NotificationsRepository.init(context)
        QuickSettingsRepository.init(context)
    }

    val liveNotifs = NotificationsRepository.live.collectAsState().value
    val historyNotifs = NotificationsRepository.history.collectAsState().value
    val qsState = QuickSettingsRepository.state.collectAsState().value

    var appsRefreshTick by rememberSaveable { mutableIntStateOf(0) }

    fun isAppEnabled(pkg: String): Boolean {
        return runCatching {
            when (pm.getApplicationEnabledSetting(pkg)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                else -> pm.getApplicationInfo(pkg, 0).enabled
            }
        }.getOrDefault(true)
    }

    val allApps by produceState(initialValue = emptyList(), appsRefreshTick) {
        value = withContext(Dispatchers.Default) {
            repo.loadApps().filter { isAppEnabled(it.packageName) }
        }
    }

    val pinned by repo.pinnedFlow.collectAsState(initial = emptySet())
    val recents by repo.recentsFlow.collectAsState(initial = emptyList())
    val placements by widgetsRepo.widgetsFlow(WidgetLayoutMode.LANDSCAPE).collectAsState(initial = emptyList())

    val allAppsGrid: List<AllAppsGridItem> = remember(allApps) {
        buildList {
            add(AllAppsGridItem.Back)
            addAll(allApps.map { AllAppsGridItem.App(it) })
        }
    }
    val allAppsLastIndex = allAppsGrid.lastIndex

    val appByPkg = remember(allApps) { allApps.associateBy { it.packageName } }

    val topApps: List<LaunchableApp> = remember(allApps, pinned, recents, appByPkg) {
        val recentsRank = recents.withIndex().associate { it.value to it.index }

        val pinnedApps = pinned
            .mapNotNull { appByPkg[it] }
            .sortedWith(compareBy<LaunchableApp>({ recentsRank[it.packageName] ?: Int.MAX_VALUE }, { it.label.lowercase() }))

        val recentApps = recents.mapNotNull { appByPkg[it] }.filter { it.packageName !in pinned }

        val base = (pinnedApps + recentApps).distinctBy { it.packageName }.toMutableList()
        if (base.size < 9) {
            val existing = base.asSequence().map { it.packageName }.toSet()
            val filler = allApps.asSequence().filter { it.packageName !in existing }.take(9 - base.size).toList()
            base.addAll(filler)
        }
        base.take(9)
    }

    val anchorIndex = 0
    val padEnd = 12

    val realItems: List<TopItem> = remember(topApps) {
        buildList {
            addAll(topApps.map { TopItem.App(it) })
            add(TopItem.AllApps)
        }
    }
    val realLastIndex = realItems.lastIndex

    val topItems: List<TopItem> = remember(realItems) {
        buildList {
            repeat(anchorIndex) { add(TopItem.Spacer) }
            addAll(realItems)
            repeat(padEnd) { add(TopItem.Spacer) }
        }
    }

    val dominantCache = remember { mutableStateMapOf<String, Color>() }
    val defaultAccent = Color(0xFF101826)
    var accent by remember { mutableStateOf(defaultAccent) }

    val selectedTopItemFn: (Int, HomeSection) -> TopItem? = remember(topItems) {
        { topIndex, homeSection ->
            val raw = topItems.getOrNull(topIndex + anchorIndex)
            if (homeSection == HomeSection.TOPBAR || homeSection == HomeSection.WIDGETS) null else raw
        }
    }
    val selectedAppFn: (Int, HomeSection) -> LaunchableApp? = remember(topItems) {
        { topIndex, homeSection ->
            (selectedTopItemFn(topIndex, homeSection) as? TopItem.App)?.app
        }
    }

    val selectedAppForAccent = selectedAppFn(0, HomeSection.TOP)
    LaunchedEffect(accentFromIcon, selectedAppForAccent?.packageName) {
        if (!accentFromIcon) {
            accent = defaultAccent
            return@LaunchedEffect
        }

        if (selectedAppForAccent == null) {
            accent = defaultAccent
        } else {
            dominantCache[selectedAppForAccent.packageName]?.let { accent = it } ?: run {
                val c = computeDominantColor(selectedAppForAccent.iconBitmap, fallback = defaultAccent)
                dominantCache[selectedAppForAccent.packageName] = c
                accent = c
            }
        }
    }

    val bg = remember(accent) {
        Brush.verticalGradient(
            listOf(
                accent.copy(alpha = 0.22f),
                Color(0xFF0A0F18),
                Color(0xFF060A11)
            )
        )
    }

    val stripState = rememberLazyListState()
    val homeScrollState = rememberLazyListState()
    val fling: FlingBehavior = ScrollableDefaults.flingBehavior()
    val columns = allAppsColumns.coerceIn(2, 5)
    val tabOrder = remember { listOf(Tab.GAMES, Tab.MEDIA, Tab.NOTIFICATIONS) }
    val okCodes = remember {
        setOf(
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_BUTTON_A
        )
    }

    return PSHomeDerivedData(
        repo = repo,
        widgetsRepo = widgetsRepo,
        allApps = allApps,
        pinned = pinned,
        recents = recents,
        placements = placements,
        liveNotifs = liveNotifs as List<Any>,
        historyNotifs = historyNotifs as List<Any>,
        qsState = qsState as Any,
        allAppsGrid = allAppsGrid,
        allAppsLastIndex = allAppsLastIndex,
        topApps = topApps,
        anchorIndex = anchorIndex,
        realItems = realItems,
        realLastIndex = realLastIndex,
        topItems = topItems,
        stripState = stripState,
        fling = fling,
        columns = columns,
        tabOrder = tabOrder,
        okCodes = okCodes,
        bg = bg,
        homeScrollState = homeScrollState,
        bumpAppsRefreshTick = { appsRefreshTick++ },
        selectedTopItem = selectedTopItemFn,
        selectedApp = selectedAppFn
    )
}

internal fun launchPsHomeApp(
    context: Context,
    pm: PackageManager,
    scope: CoroutineScope,
    repo: LauncherRepository,
    app: LaunchableApp
) {
    val intent = pm.getLaunchIntentForPackage(app.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    scope.launch { repo.pushRecent(app.packageName) }
}

internal fun openPsHomeAppInfo(
    context: Context,
    app: LaunchableApp
) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", app.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

internal fun canPsHomeUninstall(
    context: Context,
    pm: PackageManager,
    pkg: String
): Boolean {
    if (pkg == context.packageName) return false
    return runCatching {
        val ai = pm.getApplicationInfo(pkg, 0)
        val isSystemOrUpdatedSystem =
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        !isSystemOrUpdatedSystem
    }.getOrDefault(false)
}

internal fun canPsHomeDisable(
    context: Context,
    pm: PackageManager,
    pkg: String
): Boolean {
    if (pkg == context.packageName) return false
    return runCatching {
        val ai = pm.getApplicationInfo(pkg, 0)
        val isSystemOrUpdatedSystem =
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val isPersistent = (ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
        isSystemOrUpdatedSystem && !isPersistent && ai.enabled
    }.getOrDefault(false)
}

@SuppressLint("UseKtx")
internal fun requestPsHomeUninstall(
    context: Context,
    pm: PackageManager,
    app: LaunchableApp,
    openAppInfo: (Context, LaunchableApp) -> Unit
) {
    val uri = "package:${app.packageName}".toUri()

    fun fallback(reason: String? = null) {
        if (!reason.isNullOrBlank()) Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
        openAppInfo(context, app)
    }

    val intent = Intent(Intent.ACTION_DELETE).apply {
        data = uri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        if (intent.resolveActivity(pm) == null) {
            fallback(context.getString(R.string.homescreen_uninstall_no_handler))
            return
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        fallback(context.getString(R.string.homescreen_uninstall_open_failed))
    }
}
