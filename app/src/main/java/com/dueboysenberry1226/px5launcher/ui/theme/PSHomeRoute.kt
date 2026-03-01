@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.SettingsRepository
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import com.dueboysenberry1226.px5launcher.data.NotificationsRepository
import com.dueboysenberry1226.px5launcher.data.QuickSettingsRepository
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dueboysenberry1226.px5launcher.data.LaunchableApp
import com.dueboysenberry1226.px5launcher.data.LauncherRepository
import com.dueboysenberry1226.px5launcher.data.Tab
import com.dueboysenberry1226.px5launcher.data.TopItem
import com.dueboysenberry1226.px5launcher.data.WidgetGridSpec
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import com.dueboysenberry1226.px5launcher.data.WidgetsRepository
import com.dueboysenberry1226.px5launcher.ui.widgets.WidgetPickerScreen
import com.dueboysenberry1226.px5launcher.ui.widgets.rememberWidgetPickerState
import com.dueboysenberry1226.px5launcher.util.computeDominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class HomeSection { TOPBAR, TOP, ACTIONS, WIDGETS, NOTIFS }
private enum class BottomPanel { WIDGETS, CALENDAR, MUSIC }

private const val WIDGET_HOST_ID = 1024

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PSHomeRoute(
    pm: PackageManager,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val repo = remember(pm, context) { LauncherRepository(context, pm) }

    val settingsRepo = remember(context) { SettingsRepository(context) }
    val is24h by settingsRepo.clock24hFlow.collectAsState(initial = true)
    val showBigAppName by settingsRepo.showBigAppNameFlow.collectAsState(initial = true)
    val quickTileClick = rememberQuickTileClickHandler(context)
    val accentFromIcon by settingsRepo.accentFromAppIconFlow.collectAsState(initial = true)
    val vibrationEnabled by settingsRepo.vibrationEnabledFlow.collectAsState(initial = true)
    val allAppsColumns by settingsRepo.allAppsColumnsFlow.collectAsState(initial = 4)

    LaunchedEffect(Unit) {
        NotificationsRepository.init(context)
        QuickSettingsRepository.init(context)
    }

    val liveNotifs = NotificationsRepository.live.collectAsState().value
    val historyNotifs = NotificationsRepository.history.collectAsState().value
    val qsState = QuickSettingsRepository.state.collectAsState().value

    var notifHistoryMode by rememberSaveable { mutableStateOf(false) }

    var notifUpFromLeftButtonsAllowed by rememberSaveable { mutableStateOf(false) }
    var notifUpFromQsTopRowAllowed by rememberSaveable { mutableStateOf(false) }
    val notifUpToTopbarAllowed = notifUpFromLeftButtonsAllowed || notifUpFromQsTopRowAllowed

    var mediaKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
    var widgetsKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
    var calendarKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
    var musicKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }

    val widgetsRepo = remember(context) { WidgetsRepository(context) }
    val widgetHost = remember(context) { AppWidgetHost(context, WIDGET_HOST_ID) }
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }

    DisposableEffect(Unit) {
        widgetHost.startListening()
        onDispose { widgetHost.stopListening() }
    }

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

    val allApps by produceState(initialValue = emptyList<LaunchableApp>(), appsRefreshTick) {
        value = withContext(Dispatchers.Default) {
            repo.loadApps().filter { isAppEnabled(it.packageName) }
        }
    }

    val pinned by repo.pinnedFlow.collectAsState(initial = emptySet())
    val recents by repo.recentsFlow.collectAsState(initial = emptyList())
    val placements by widgetsRepo.widgetsFlow.collectAsState(initial = emptyList())

    val cfg = LocalConfiguration.current
    val gridSpec = remember(cfg.screenWidthDp) {
        val cols = if (cfg.screenWidthDp >= 900) 4 else 3
        WidgetGridSpec(cols = cols, rows = 4)
    }

    var tab by rememberSaveable { mutableStateOf(Tab.GAMES) }
    var topIndex by rememberSaveable { mutableIntStateOf(0) }

    var topBarIndex by rememberSaveable {
        mutableIntStateOf(
            when (tab) {
                Tab.GAMES -> 0
                Tab.MEDIA -> 1
                Tab.NOTIFICATIONS -> 2
            }
        )
    }

    var allAppsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var allAppsSelectedIndex by rememberSaveable { mutableIntStateOf(0) }

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var menuTargetApp by remember { mutableStateOf<LaunchableApp?>(null) }

    var homeSection by rememberSaveable { mutableStateOf(HomeSection.TOP) }
    var actionIndex by rememberSaveable { mutableIntStateOf(0) }
    val homeScrollState = rememberLazyListState()

    var bottomPanel by rememberSaveable { mutableStateOf(BottomPanel.WIDGETS) }

    val playFR = remember { FocusRequester() }
    val menuFR = remember { FocusRequester() }

    val widgetsFR = remember { FocusRequester() }
    val calendarFR = remember { FocusRequester() }
    val musicFR = remember { FocusRequester() }

    fun currentPanelFR(): FocusRequester = when (bottomPanel) {
        BottomPanel.WIDGETS -> widgetsFR
        BottomPanel.CALENDAR -> calendarFR
        BottomPanel.MUSIC -> musicFR
    }

    fun focusActions() {
        if (actionIndex == 0) playFR.requestFocus() else menuFR.requestFocus()
    }

    fun goHomeTopKeepScroll() {
        scope.launch { homeScrollState.animateScrollToItem(0) }
    }

    fun goWidgets() {
        scope.launch {
            homeScrollState.animateScrollToItem(1)
            kotlinx.coroutines.yield()
            currentPanelFR().requestFocus()
        }
    }

    fun goActions() {
        scope.launch {
            homeScrollState.animateScrollToItem(0)
            kotlinx.coroutines.yield()
            focusActions()
        }
    }

    fun setBottomPanel(next: BottomPanel) {
        if (bottomPanel == next) return
        if (next != BottomPanel.WIDGETS) widgetsKeyHandler = null
        bottomPanel = next
    }

    fun stepBottomPanel(delta: Int) {
        val order = listOf(BottomPanel.WIDGETS, BottomPanel.CALENDAR, BottomPanel.MUSIC)
        val idx = order.indexOf(bottomPanel).takeIf { it >= 0 } ?: 0
        val size = order.size
        val nextIdx = ((idx + delta) % size + size) % size
        setBottomPanel(order[nextIdx])
    }

    var clockText by remember { mutableStateOf("") }

    LaunchedEffect(is24h) {
        val pattern = if (is24h) "HH:mm" else "hh:mm a"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())

        while (true) {
            clockText = fmt.format(Date())
            delay(1_000)
        }
    }

    LaunchedEffect(tab) {
        topBarIndex = when (tab) {
            Tab.GAMES -> 0
            Tab.MEDIA -> 1
            Tab.NOTIFICATIONS -> 2
        }

        if (tab == Tab.MEDIA || tab == Tab.NOTIFICATIONS) {
            homeSection = HomeSection.TOPBAR
            allAppsOpen = false
            menuOpen = false
            menuTargetApp = null
            searchOpen = false
            searchQuery = ""
            focusManager.clearFocus(force = true)
        }
    }

    val allAppsGrid: List<AllAppsGridItem> = remember(allApps) {
        buildList {
            add(AllAppsGridItem.Back)
            addAll(allApps.map { AllAppsGridItem.App(it) })
        }
    }
    val allAppsLastIndex = allAppsGrid.lastIndex

    val appByPkg = remember(allApps) { allApps.associateBy { it.packageName } }

    val topApps: List<LaunchableApp> = remember(allApps, pinned, recents) {
        fun recencyRank(pkg: String): Int {
            val idx = recents.indexOf(pkg)
            return if (idx >= 0) idx else Int.MAX_VALUE
        }

        val pinnedApps = pinned
            .mapNotNull { appByPkg[it] }
            .sortedWith(compareBy<LaunchableApp>({ recencyRank(it.packageName) }, { it.label.lowercase() }))

        val recentApps = recents
            .mapNotNull { appByPkg[it] }
            .filter { it.packageName !in pinned }

        val base = (pinnedApps + recentApps)
            .distinctBy { it.packageName }
            .toMutableList()

        if (base.size < 9) {
            val existing = base.asSequence().map { it.packageName }.toSet()
            val filler = allApps.asSequence()
                .filter { it.packageName !in existing }
                .take(9 - base.size)
                .toList()
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

    LaunchedEffect(realItems.size) {
        if (topIndex !in 0..realLastIndex) topIndex = 0
    }

    val topItems: List<TopItem> = remember(realItems) {
        buildList {
            repeat(anchorIndex) { add(TopItem.Spacer) }
            addAll(realItems)
            repeat(padEnd) { add(TopItem.Spacer) }
        }
    }

    val displayIndex = topIndex + anchorIndex
    val rawSelectedTopItem = topItems.getOrNull(displayIndex)

    val uiHidesSelectedApp = homeSection == HomeSection.TOPBAR || homeSection == HomeSection.WIDGETS
    val selectedTopItem: TopItem? = if (uiHidesSelectedApp) null else rawSelectedTopItem
    val selectedApp: LaunchableApp? = (selectedTopItem as? TopItem.App)?.app

    val dominantCache = remember { mutableStateMapOf<String, Color>() }
    val defaultAccent = Color(0xFF101826)
    var accent by remember { mutableStateOf(defaultAccent) }

    LaunchedEffect(accentFromIcon, selectedApp?.packageName) {
        if (!accentFromIcon) {
            accent = defaultAccent
            return@LaunchedEffect
        }

        val app = selectedApp
        if (app == null) {
            accent = defaultAccent
        } else {
            dominantCache[app.packageName]?.let { accent = it } ?: run {
                val c = computeDominantColor(app.iconBitmap, fallback = defaultAccent)
                dominantCache[app.packageName] = c
                accent = c
            }
        }
    }

    val bg = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(
            accent.copy(alpha = 0.22f),
            Color(0xFF0A0F18),
            Color(0xFF060A11)
        )
    )

    fun launchApp(app: LaunchableApp) {
        val intent = pm.getLaunchIntentForPackage(app.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        scope.launch { repo.pushRecent(app.packageName) }
    }

    fun openAppInfo(app: LaunchableApp) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", app.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canUninstall(pkg: String): Boolean {
        if (pkg == context.packageName) return false
        return runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            val isSystemOrUpdatedSystem =
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            !isSystemOrUpdatedSystem
        }.getOrDefault(false)
    }

    fun canDisable(pkg: String): Boolean {
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

    fun requestUninstall(app: LaunchableApp) {
        val pkg = app.packageName
        val uri = Uri.parse("package:$pkg")

        fun fallback(reason: String? = null) {
            if (!reason.isNullOrBlank()) Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
            openAppInfo(app)
        }

        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (intent.resolveActivity(context.packageManager) == null) {
                fallback(context.getString(R.string.homescreen_uninstall_no_handler))
                return
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            fallback(context.getString(R.string.homescreen_uninstall_open_failed))
        }
    }

    fun openMenuFor(app: LaunchableApp?) {
        if (app == null) return
        menuTargetApp = app
        menuOpen = true
    }

    fun closeMenu() {
        menuOpen = false
        menuTargetApp = null
    }

    LaunchedEffect(allAppsOpen) {
        if (allAppsOpen) {
            appsRefreshTick++
            // -2 = none, -1 = back, 0.. = app
            allAppsSelectedIndex = allAppsSelectedIndex.coerceIn(-2, (allApps.size - 1).coerceAtLeast(0))
            // ha véletlen none-ban nyitna: induljunk 0-ról
            if (allAppsSelectedIndex == -2) allAppsSelectedIndex = 0
        }
    }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 52.dp.toPx() }

    // ----- Widget dolgok -----
    var cellPx by remember { mutableFloatStateOf(0f) }
    val handledWidgetIds = remember { mutableSetOf<Int>() }

    data class PendingWidgetAdd(
        val cellX: Int,
        val cellY: Int,
        val appWidgetId: Int,
        val provider: ComponentName,
        val spanX: Int,
        val spanY: Int
    )

    var pendingAdd by remember { mutableStateOf<PendingWidgetAdd?>(null) }

    fun showToastWidget(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun canPlaceAtWidget(
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
            for (dy in 0 until p.spanY) for (dx in 0 until p.spanX) {
                val x = p.cellX + dx
                val y = p.cellY + dy
                if (y in 0 until gridSpec.rows && x in 0 until gridSpec.cols) {
                    occ[y][x] = true
                }
            }
        }

        for (dy in 0 until spanY) for (dx in 0 until spanX) {
            val x = cellX + dx
            val y = cellY + dy
            if (occ[y][x]) return false
        }
        return true
    }

    fun resolveAnchorForTap(
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

        for (y in minY..maxY) for (x in minX..maxX) {
            candidates += x to y
        }

        val sorted = candidates.sortedBy { (x, y) ->
            abs(x - tapX) + abs(y - tapY)
        }

        return sorted.firstOrNull { (x, y) ->
            canPlaceAtWidget(x, y, spanX, spanY, ignoreAppWidgetId)
        }
    }

    fun nextClockwiseSlotWidget(
        spanX: Int,
        spanY: Int,
        startX: Int,
        startY: Int,
        ignoreAppWidgetId: Int? = null
    ): Pair<Int, Int>? {
        val total = gridSpec.cols * gridSpec.rows
        val startIdx = (startY * gridSpec.cols + startX).coerceIn(0, total - 1)

        for (step in 1..total) {
            val idx = (startIdx + step) % total
            val x = idx % gridSpec.cols
            val y = idx / gridSpec.cols
            if (canPlaceAtWidget(x, y, spanX, spanY, ignoreAppWidgetId)) return x to y
        }
        return null
    }

    fun finalizeAddWidget(appWidgetId: Int, info: AppWidgetProviderInfo, cellX: Int, cellY: Int) {
        val pend = pendingAdd
        val sx = pend?.spanX ?: 2
        val sy = pend?.spanY ?: 2

        if (!canPlaceAtWidget(cellX, cellY, sx, sy)) {
            runCatching { widgetHost.deleteAppWidgetId(appWidgetId) }
            handledWidgetIds.remove(appWidgetId)
            pendingAdd = null
            showToastWidget(context.getString(R.string.homescreen_widget_no_space_for_size))
            return
        }

        val providerStr = info.provider.flattenToString()
        scope.launch {
            widgetsRepo.upsert(
                WidgetPlacement(
                    appWidgetId = appWidgetId,
                    provider = providerStr,
                    cellX = cellX,
                    cellY = cellY,
                    spanX = sx,
                    spanY = sy
                )
            )
        }
        pendingAdd = null
    }

    val configureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val pend = pendingAdd ?: return@rememberLauncherForActivityResult
        val id = pend.appWidgetId

        if (res.resultCode != Activity.RESULT_OK) {
            runCatching { widgetHost.deleteAppWidgetId(id) }
            handledWidgetIds.remove(id)
            pendingAdd = null
            return@rememberLauncherForActivityResult
        }

        val info = widgetManager.getAppWidgetInfo(id)
        if (info == null) {
            runCatching { widgetHost.deleteAppWidgetId(id) }
            handledWidgetIds.remove(id)
            pendingAdd = null
            return@rememberLauncherForActivityResult
        }

        finalizeAddWidget(id, info, pend.cellX, pend.cellY)
    }

    fun afterBoundWidget(appWidgetId: Int, cellX: Int, cellY: Int) {
        if (!handledWidgetIds.add(appWidgetId)) return

        val info = widgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            runCatching { widgetHost.deleteAppWidgetId(appWidgetId) }
            handledWidgetIds.remove(appWidgetId)
            pendingAdd = null
            return
        }

        if (info.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            configureLauncher.launch(intent)
        } else {
            finalizeAddWidget(appWidgetId, info, cellX, cellY)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val pend = pendingAdd ?: return@rememberLauncherForActivityResult
        val id = pend.appWidgetId

        if (res.resultCode != Activity.RESULT_OK && res.resultCode != Activity.RESULT_CANCELED) {
            runCatching { widgetHost.deleteAppWidgetId(id) }
            handledWidgetIds.remove(id)
            pendingAdd = null
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            var info: AppWidgetProviderInfo? = null
            repeat(40) {
                info = widgetManager.getAppWidgetInfo(id)
                if (info != null) return@repeat
                delay(50)
            }

            if (info == null) {
                runCatching { widgetHost.deleteAppWidgetId(id) }
                handledWidgetIds.remove(id)
                pendingAdd = null
                showToastWidget("Nem lett bindelve a widget (AppWidgetInfo null). id=$id")
                return@launch
            }

            afterBoundWidget(id, pend.cellX, pend.cellY)
        }
    }

    var showWidgetPicker by rememberSaveable { mutableStateOf(false) }
    var pendingPickCellX by remember { mutableIntStateOf(0) }
    var pendingPickCellY by remember { mutableIntStateOf(0) }
    var allAppsLastAppIndex by remember { mutableIntStateOf(0) }

    val cellDp = remember(cellPx) {
        if (cellPx > 0f) with(density) { cellPx.toDp() } else 90.dp
    }

    val pickerState = rememberWidgetPickerState(
        pm = pm,
        appWidgetManager = widgetManager,
        cellWidthDp = cellDp,
        cellHeightDp = cellDp,
        onPick = { providerInfo, sx, sy ->

            if (vibrationEnabled) Haptics.click(context)

            val provider = providerInfo.provider ?: run {
                showToastWidget(context.getString(R.string.homescreen_widget_provider_null))
                return@rememberWidgetPickerState
            }

            val anchor = resolveAnchorForTap(
                tapX = pendingPickCellX,
                tapY = pendingPickCellY,
                spanX = sx,
                spanY = sy,
                ignoreAppWidgetId = null
            )

            if (anchor == null) {
                showToastWidget(context.getString(R.string.homescreen_widget_no_space_here))
                return@rememberWidgetPickerState
            }

            val (cellX, cellY) = anchor
            showWidgetPicker = false

            val appWidgetId = widgetHost.allocateAppWidgetId()
            handledWidgetIds.remove(appWidgetId)

            pendingAdd = PendingWidgetAdd(
                cellX = cellX,
                cellY = cellY,
                appWidgetId = appWidgetId,
                provider = provider,
                spanX = sx,
                spanY = sy
            )

            val bound = runCatching {
                widgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
            }.getOrDefault(false)

            if (bound) {
                afterBoundWidget(appWidgetId, cellX, cellY)
            } else {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                }
                bindLauncher.launch(intent)
            }
        },
        onBack = {
            if (vibrationEnabled) Haptics.click(context)
            showWidgetPicker = false }
    )

    fun requestAddAt(cellX: Int, cellY: Int) {
        pendingPickCellX = cellX
        pendingPickCellY = cellY
        pickerState.updateQuery("")
        pickerState.selectIndex(0)
        showWidgetPicker = true
    }

    fun deleteWidget(p: WidgetPlacement) {
        scope.launch { widgetsRepo.remove(p.appWidgetId) }
        runCatching { widgetHost.deleteAppWidgetId(p.appWidgetId) }
        handledWidgetIds.remove(p.appWidgetId)
    }

    fun moveWidgetClockwise(p: WidgetPlacement) {
        val next = nextClockwiseSlotWidget(
            spanX = p.spanX,
            spanY = p.spanY,
            startX = p.cellX,
            startY = p.cellY,
            ignoreAppWidgetId = p.appWidgetId
        )

        if (next == null) {
            showToastWidget(context.getString(R.string.homescreen_widget_no_empty_slot))
            return
        }

        val (nx, ny) = next
        scope.launch { widgetsRepo.upsert(p.copy(cellX = nx, cellY = ny)) }
    }

    // ✅ GLOBAL BACK: soha ne zárja be az Activity-t + overlay-ket zárjon
    BackHandler(enabled = true) {
        when {
            showWidgetPicker -> showWidgetPicker = false
            menuOpen -> closeMenu()
            searchOpen -> { searchOpen = false; searchQuery = "" }
            allAppsOpen -> allAppsOpen = false
            else -> {
                // elnyeljük: ne lépjen ki az appból
            }
        }
    }

    // ---------------- TOP STRIP ----------------
    val stripState = rememberLazyListState()
    val fling: FlingBehavior = ScrollableDefaults.flingBehavior()

    fun scrollSelectedToAnchor(animated: Boolean) {
        if (topItems.isEmpty()) return
        val idx = (topIndex + anchorIndex).coerceIn(0, topItems.lastIndex)
        scope.launch {
            if (animated) stripState.animateScrollToItem(idx)
            else stripState.scrollToItem(idx)
        }
    }

    LaunchedEffect(topItems.size) { scrollSelectedToAnchor(animated = false) }
    LaunchedEffect(topIndex) { scrollSelectedToAnchor(animated = true) }

    fun stepStripByController(delta: Int) {
        if (delta == 0) return
        val next = (topIndex + delta).coerceIn(0, realLastIndex)
        if (next == topIndex) return
        topIndex = next
    }

    val columns = allAppsColumns.coerceIn(2, 5)

    val keyHandler: (KeyEvent) -> Boolean = { e ->
        val nk = e.nativeKeyEvent
        val code = nk.keyCode
        val action = nk.action

        val okCodes = setOf(
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A
        )

        val isLB = (code == AndroidKeyEvent.KEYCODE_BUTTON_L1) || (code == AndroidKeyEvent.KEYCODE_1)
        val isRB = (code == AndroidKeyEvent.KEYCODE_BUTTON_R1) || (code == AndroidKeyEvent.KEYCODE_2)

        if (homeSection == HomeSection.TOPBAR && code in okCodes) {
            if (action == AndroidKeyEvent.ACTION_DOWN || action == AndroidKeyEvent.ACTION_UP) {
                tab = when (topBarIndex) {
                    0 -> Tab.GAMES
                    1 -> Tab.MEDIA
                    else -> Tab.NOTIFICATIONS
                }
                true
            } else false
        } else if (action != AndroidKeyEvent.ACTION_DOWN) {
            false
        } else {

            if (vibrationEnabled) {
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_BUTTON_A,
                    AndroidKeyEvent.KEYCODE_BUTTON_B,
                    AndroidKeyEvent.KEYCODE_BACK -> {
                        Haptics.click(context)
                    }
                }
            }

            if (!allAppsOpen && (isLB || isRB)) {
                if (homeSection == HomeSection.WIDGETS) {
                    stepBottomPanel(if (isLB) -1 else +1)
                    true
                } else {
                    val order = listOf(Tab.GAMES, Tab.MEDIA, Tab.NOTIFICATIONS)
                    val idx = order.indexOf(tab).takeIf { it >= 0 } ?: 0
                    val nextIdx = ((idx + (if (isLB) -1 else +1)) % order.size + order.size) % order.size
                    val nextTab = order[nextIdx]
                    if (tab != nextTab) tab = nextTab
                    true
                }
            } else if (homeSection == HomeSection.TOPBAR) {
                when (code) {

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (allAppsOpen) {
                            // ✅ TOPBAR-ról LE: vissza az AllApps első app tile-ra
                            allAppsSelectedIndex = 0
                            homeSection = HomeSection.TOP
                            focusManager.clearFocus(force = true)
                            true
                        } else {
                            // normál működés (amikor nincs AllApps)
                            when (tab) {
                                Tab.GAMES -> homeSection = HomeSection.TOP
                                Tab.MEDIA -> homeSection = HomeSection.TOP
                                Tab.NOTIFICATIONS -> homeSection = HomeSection.NOTIFS
                            }
                            true
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_UP -> true

                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        // 0..2 tabok, 3=search, 4=settings
                        topBarIndex = (topBarIndex - 1).coerceAtLeast(0)
                        // tab csak akkor változzon, ha tab indexen állunk
                        if (topBarIndex <= 2) {
                            tab = when (topBarIndex) {
                                0 -> Tab.GAMES
                                1 -> Tab.MEDIA
                                else -> Tab.NOTIFICATIONS
                            }
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        topBarIndex = (topBarIndex + 1).coerceAtMost(4)
                        if (topBarIndex <= 2) {
                            tab = when (topBarIndex) {
                                0 -> Tab.GAMES
                                1 -> Tab.MEDIA
                                else -> Tab.NOTIFICATIONS
                            }
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                        when (topBarIndex) {
                            0 -> tab = Tab.GAMES
                            1 -> tab = Tab.MEDIA
                            2 -> tab = Tab.NOTIFICATIONS
                            3 -> searchOpen = true
                            4 -> onOpenSettings()
                        }
                        true
                    }

                    AndroidKeyEvent.KEYCODE_BACK -> true

                    else -> false
                }
            } else if (allAppsOpen) {
                val lastApp = (allApps.size - 1).coerceAtLeast(0)

                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        when {
                            allAppsSelectedIndex == -1 -> true
                            allAppsSelectedIndex % columns == 0 -> { allAppsSelectedIndex = -1; true }
                            allAppsSelectedIndex > 0 -> { allAppsSelectedIndex--; true }
                            else -> true
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when {
                            allAppsSelectedIndex == -1 -> { allAppsSelectedIndex = 0; true }
                            allAppsSelectedIndex < lastApp -> { allAppsSelectedIndex++; true }
                            else -> true
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        when {
                            // Back selected esetén: fel -> TOPBAR, és maradjon Back selected (OK)
                            allAppsSelectedIndex == -1 -> {
                                homeSection = HomeSection.TOPBAR
                                topBarIndex = when (tab) {
                                    Tab.GAMES -> 0
                                    Tab.MEDIA -> 1
                                    Tab.NOTIFICATIONS -> 2
                                }
                                focusManager.clearFocus(force = true)
                                true
                            }

                            // ✅ FELSŐ SORBÓL: fel -> TOPBAR, DE NEM állítjuk -1-re
                            allAppsSelectedIndex < columns -> {
                                // jegyezzük meg, melyik app volt kijelölve
                                allAppsLastAppIndex = allAppsSelectedIndex.coerceAtLeast(0)

                                // topbar
                                homeSection = HomeSection.TOPBAR
                                topBarIndex = when (tab) {
                                    Tab.GAMES -> 0
                                    Tab.MEDIA -> 1
                                    Tab.NOTIFICATIONS -> 2
                                }

                                // ✅ vedd le a selected-et az appról
                                allAppsSelectedIndex = -2

                                focusManager.clearFocus(force = true)
                                true
                            }

                            else -> {
                                allAppsSelectedIndex = (allAppsSelectedIndex - columns).coerceAtLeast(0)
                                true
                            }
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        when {
                            // NONE állapotból ne csináljon semmit
                            allAppsSelectedIndex == -2 -> true

                            // Back-ről lefelé: első app
                            allAppsSelectedIndex == -1 -> {
                                allAppsSelectedIndex = 0
                                true
                            }

                            else -> {
                                val next = allAppsSelectedIndex + columns
                                if (next <= lastApp) {
                                    allAppsSelectedIndex = next
                                }
                                true
                            }
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                        if (allAppsSelectedIndex == -1) {
                            allAppsOpen = false
                            true
                        } else if (allAppsSelectedIndex == -2) {
                            // NONE: a TOPBAR irányít, itt ne történjen semmi
                            true
                        } else {
                            val app = allApps.getOrNull(allAppsSelectedIndex)
                            if (app != null) {
                                allAppsOpen = false
                                launchApp(app)
                            }
                            true
                        }
                    }

                    AndroidKeyEvent.KEYCODE_BUTTON_B,
                    AndroidKeyEvent.KEYCODE_BACK -> {
                        allAppsOpen = false
                        true
                    }

                    else -> false
                }
            } else {
                when (homeSection) {
                    HomeSection.NOTIFS -> when (code) {

                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (notifUpToTopbarAllowed) {
                                homeSection = HomeSection.TOPBAR
                                topBarIndex = when (tab) {
                                    Tab.GAMES -> 0
                                    Tab.MEDIA -> 1
                                    Tab.NOTIFICATIONS -> 2
                                }
                                focusManager.clearFocus(force = true)
                                true
                            } else false
                        }

                        AndroidKeyEvent.KEYCODE_BACK -> {
                            homeSection = HomeSection.TOPBAR
                            focusManager.clearFocus(force = true)
                            true
                        }

                        else -> false
                    }

                    HomeSection.TOP -> {
                        when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { stepStripByController(-1); true }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { stepStripByController(+1); true }

                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                homeSection = HomeSection.TOPBAR
                                topBarIndex = when (tab) {
                                    Tab.GAMES -> 0
                                    Tab.MEDIA -> 1
                                    Tab.NOTIFICATIONS -> 2
                                }
                                focusManager.clearFocus(force = true)
                                true
                            }

                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                homeSection = HomeSection.ACTIONS
                                actionIndex = 0
                                goActions()
                                true
                            }

                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                when (val it = selectedTopItem) {
                                    is TopItem.App -> launchApp(it.app)
                                    is TopItem.AllApps -> {
                                        allAppsOpen = true
                                        allAppsSelectedIndex =
                                            allAppsSelectedIndex.coerceIn(0, allAppsLastIndex.coerceAtLeast(0))
                                    }
                                    else -> Unit
                                }
                                true
                            }

                            AndroidKeyEvent.KEYCODE_BUTTON_Y -> { searchOpen = true; true }

                            AndroidKeyEvent.KEYCODE_BUTTON_X -> {
                                val app = selectedApp
                                if (app != null) {
                                    scope.launch { repo.setPinned(app.packageName, value = app.packageName !in pinned) }
                                    true
                                } else false
                            }

                            AndroidKeyEvent.KEYCODE_BACK -> {
                                when {
                                    menuOpen -> { closeMenu(); true }
                                    searchOpen -> { searchOpen = false; searchQuery = ""; true }
                                    else -> true // elnyeljük
                                }
                            }

                            else -> false
                        }
                    }

                    HomeSection.ACTIONS -> {
                        when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { actionIndex = 0; focusActions(); true }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { actionIndex = 1; focusActions(); true }

                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                homeSection = HomeSection.TOP
                                focusManager.clearFocus(force = true)
                                goHomeTopKeepScroll()
                                true
                            }

                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                homeSection = HomeSection.WIDGETS
                                goWidgets()
                                true
                            }

                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                                val canLaunchNow = selectedTopItem is TopItem.App
                                if (actionIndex == 0) {
                                    if (canLaunchNow) (selectedTopItem as? TopItem.App)?.app?.let { launchApp(it) }
                                } else {
                                    if (canLaunchNow) openMenuFor((selectedTopItem as? TopItem.App)?.app)
                                }
                                true
                            }

                            AndroidKeyEvent.KEYCODE_BACK -> {
                                when {
                                    menuOpen -> { closeMenu(); true }
                                    searchOpen -> { searchOpen = false; searchQuery = ""; true }
                                    else -> true
                                }
                            }

                            else -> false
                        }
                    }

                    HomeSection.WIDGETS -> {
                        val handledByPanel = when (bottomPanel) {
                            BottomPanel.WIDGETS -> widgetsKeyHandler?.invoke(e) == true
                            BottomPanel.CALENDAR -> calendarKeyHandler?.invoke(e) == true
                            BottomPanel.MUSIC -> musicKeyHandler?.invoke(e) == true
                        }

                        if (handledByPanel) true
                        else when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> { homeSection = HomeSection.ACTIONS; goActions(); true }
                            AndroidKeyEvent.KEYCODE_BACK,
                            AndroidKeyEvent.KEYCODE_BUTTON_B -> { homeSection = HomeSection.ACTIONS; goActions(); true }
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                            else -> false
                        }
                    }
                    HomeSection.TOPBAR -> false
                }
            }
        }
    }

    // ---------------- UI ----------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(allAppsOpen, showWidgetPicker, tab, homeSection, bottomPanel, menuOpen, searchOpen) {
                if (allAppsOpen || showWidgetPicker || menuOpen || searchOpen) return@pointerInput
                if (tab == Tab.MEDIA || tab == Tab.NOTIFICATIONS) return@pointerInput

                val appStepPx = 96.dp.toPx()
                val tapSlopPx = 10.dp.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val id = down.id

                    var totalX = 0f
                    var totalY = 0f

                    var stepAccX = 0f
                    var steppedCount = 0
                    var dragDominant: Boolean? = null

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == id } ?: break
                        if (!change.pressed) break

                        val d = change.positionChange()
                        totalX += d.x
                        totalY += d.y

                        if (abs(totalX) + abs(totalY) > 2f) change.consume()

                        val axNow = abs(totalX)
                        val ayNow = abs(totalY)
                        val maxNow = kotlin.math.max(axNow, ayNow)

                        if (dragDominant == null && maxNow >= swipeThresholdPx) {
                            dragDominant = (axNow > ayNow)
                        }

                        if (dragDominant == true) {
                            if (homeSection != HomeSection.WIDGETS && homeSection != HomeSection.TOPBAR) {
                                stepAccX += d.x

                                while (stepAccX <= -appStepPx) {
                                    stepAccX += appStepPx
                                    if (steppedCount < 12) {
                                        stepStripByController(+1)
                                        steppedCount++
                                    } else break
                                }
                                while (stepAccX >= appStepPx) {
                                    stepAccX -= appStepPx
                                    if (steppedCount < 12) {
                                        stepStripByController(-1)
                                        steppedCount++
                                    } else break
                                }
                            }
                        }
                    }

                    val ax = abs(totalX)
                    val ay = abs(totalY)
                    val maxA = kotlin.math.max(ax, ay)

                    if (maxA < tapSlopPx) {
                        if (homeSection == HomeSection.TOPBAR) {
                            homeSection = HomeSection.TOP
                            focusManager.clearFocus(force = true)
                            goHomeTopKeepScroll()
                        }
                        return@awaitEachGesture
                    }

                    if (maxA < swipeThresholdPx) return@awaitEachGesture

                    if (homeSection == HomeSection.TOPBAR) {
                        if (ay >= ax) {
                            if (totalY < 0f) {
                                if (homeSection != HomeSection.WIDGETS) {
                                    homeSection = HomeSection.WIDGETS
                                    goWidgets()
                                }
                            } else {
                                homeSection = HomeSection.TOP
                                focusManager.clearFocus(force = true)
                                goHomeTopKeepScroll()
                            }
                        }
                        return@awaitEachGesture
                    }

                    if (ax > ay) {
                        if (homeSection == HomeSection.WIDGETS) {
                            if (totalX < 0f) stepBottomPanel(+1) else stepBottomPanel(-1)
                        } else {
                            // no-op (gear step already did it)
                        }
                    } else {
                        if (totalY < 0f) {
                            if (homeSection != HomeSection.WIDGETS) {
                                homeSection = HomeSection.WIDGETS
                                goWidgets()
                            }
                        } else {
                            if (homeSection != HomeSection.TOP) {
                                homeSection = HomeSection.TOP
                                focusManager.clearFocus(force = true)
                                goHomeTopKeepScroll()
                            }
                        }
                    }
                }
            }
            .onPreviewKeyEvent { e ->
                val nk = e.nativeKeyEvent

                // 1️⃣ WidgetPicker: itt is kezeljük
                if (showWidgetPicker) {
                    // ✅ haptic a “művelet” gombokra a pickerben (kontroller)
                    if (nk.action == AndroidKeyEvent.ACTION_DOWN) {
                        when (nk.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A,
                            AndroidKeyEvent.KEYCODE_BACK,
                            AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                if (vibrationEnabled) Haptics.click(context)
                            }
                        }
                    }

                    val handled = pickerState.handleKey(e)
                    if (handled) return@onPreviewKeyEvent true

                    // (fallback) ha valamiért nem kezelte:
                    if (nk.action == AndroidKeyEvent.ACTION_DOWN &&
                        (nk.keyCode == AndroidKeyEvent.KEYCODE_BACK || nk.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE)
                    ) {
                        showWidgetPicker = false
                        return@onPreviewKeyEvent true
                    }

                    return@onPreviewKeyEvent false
                }

                // 2️⃣ BACK: ne zárja be az Activity-t
                if (nk.action == AndroidKeyEvent.ACTION_DOWN &&
                    nk.keyCode == AndroidKeyEvent.KEYCODE_BACK
                ) {
                    // BackHandler úgyis elintézi, de itt is lenyeljük
                    return@onPreviewKeyEvent true
                }

                // 3️⃣ Media routing
                if (tab == Tab.MEDIA && homeSection != HomeSection.TOPBAR) {
                    val handledByMedia = (mediaKeyHandler?.invoke(e) == true)
                    if (handledByMedia) return@onPreviewKeyEvent true

                    if (nk.action == AndroidKeyEvent.ACTION_DOWN &&
                        nk.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                    ) {
                        return@onPreviewKeyEvent keyHandler(e)
                    }

                    return@onPreviewKeyEvent false
                }

                // 4️⃣ ALL APPS: ilyenkor a KEYHANDLER irányít mindent (nincs fókusz-mix)
                if (allAppsOpen) {
                    val nk = e.nativeKeyEvent

                    if (nk.action == AndroidKeyEvent.ACTION_DOWN &&
                        (nk.keyCode == AndroidKeyEvent.KEYCODE_BACK ||
                                nk.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_B)
                    ) {
                        allAppsOpen = false
                        return@onPreviewKeyEvent true
                    }

                    // ✅ AllApps alatt MINDEN gomb a keyHandler-hez megy
                    return@onPreviewKeyEvent keyHandler(e)
                }

// 5️⃣ Normal routing
                InputController.route(
                    e = e,
                    state = InputController.State(
                        if (homeSection == HomeSection.TOPBAR)
                            InputController.Zone.TOPBAR
                        else
                            InputController.Zone.CONTENT
                    ),
                    topBarHandler = { ev -> keyHandler(ev) },
                    contentHandler = { keyHandler(it) }
                )
            }
            .background(bg)
            .padding(horizontal = 26.dp, vertical = 18.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(
                tab = tab,
                clockText = clockText,
                onTabChange = { tab = it },
                onSearch = { searchOpen = true },
                onSettings = onOpenSettings,
                topBarFocused = (homeSection == HomeSection.TOPBAR),
                topBarIndex = topBarIndex
            )

            Spacer(Modifier.height(14.dp))

            if (tab == Tab.MEDIA) {
                MediaRoute(
                    pm = pm,
                    onRequestBackToGames = { tab = Tab.GAMES },
                    hubSelectionEnabled = (homeSection != HomeSection.TOPBAR),
                    registerKeyHandler = { handler -> mediaKeyHandler = handler },
                    vibrationEnabled = vibrationEnabled
                )
            } else if (tab == Tab.NOTIFICATIONS) {
                NotificationsTabScreen(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    liveNotifications = liveNotifs,
                    historyNotifications = historyNotifs,
                    historyMode = notifHistoryMode,
                    onLeftButtonsFocusEdgeChanged = { focused -> notifUpFromLeftButtonsAllowed = focused },
                    onQsTopRowFocusEdgeChanged = { focused -> notifUpFromQsTopRowAllowed = focused },
                    onDismissOne = { id, fromHistory ->
                        if (fromHistory) NotificationsRepository.removeFromHistory(id)
                        else NotificationsRepository.dismissLiveToHistory(id)
                    },
                    onClearAll = { fromHistory ->
                        if (fromHistory) NotificationsRepository.clearHistory()
                        else NotificationsRepository.clearLiveToHistory()
                    },
                    onToggleHistoryMode = { notifHistoryMode = !notifHistoryMode },
                    tilesState = qsState,
                    onTileClick = quickTileClick,
                    onTileRemove = { slotIndex -> QuickSettingsRepository.remove(slotIndex) },
                    onTileAssign = { slotIndex, type -> QuickSettingsRepository.assign(slotIndex, type) }
                )
            } else {
                if (allAppsOpen) {
                    AllAppsScreen(
                        items = allAppsGrid,
                        selectedIndex = allAppsSelectedIndex,
                        columns = columns,
                        onSelectChange = { allAppsSelectedIndex = it },
                        onLaunch = { a ->
                            allAppsOpen = false
                            launchApp(a)
                        },
                        onBack = { allAppsOpen = false },
                        onLongPress = { a ->
                            allAppsSelectedIndex = allAppsGrid.indexOfFirst {
                                it is AllAppsGridItem.App && it.app.packageName == a.packageName
                            }.coerceAtLeast(0)
                            openMenuFor(a)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = homeScrollState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            HomeTopStrip(
                                items = topItems,
                                displayIndex = displayIndex,
                                pinned = pinned,
                                stripState = stripState,
                                fling = fling,
                                showSelection = !uiHidesSelectedApp,
                                modifier = Modifier.fillMaxWidth(),
                                onSelectIndex = { idx ->
                                    val target = (idx - anchorIndex)
                                    if (target in 0..realLastIndex) topIndex = target
                                },
                                onActivate = { item ->
                                    if (homeSection == HomeSection.TOPBAR) {
                                        homeSection = HomeSection.TOP
                                        focusManager.clearFocus(force = true)
                                        goHomeTopKeepScroll()

                                        when (item) {
                                            is TopItem.App -> {
                                                val target = realItems.indexOfFirst {
                                                    it is TopItem.App && it.app.packageName == item.app.packageName
                                                }
                                                if (target >= 0) topIndex = target
                                            }
                                            is TopItem.AllApps -> {
                                                val target = realItems.indexOfFirst { it is TopItem.AllApps }
                                                if (target >= 0) topIndex = target
                                            }
                                            else -> Unit
                                        }
                                    } else {
                                        when (item) {
                                            is TopItem.App -> {
                                                val curPkg = (rawSelectedTopItem as? TopItem.App)?.app?.packageName
                                                if (curPkg == item.app.packageName) {
                                                    launchApp(item.app)
                                                } else {
                                                    val target = realItems.indexOfFirst {
                                                        it is TopItem.App && it.app.packageName == item.app.packageName
                                                    }
                                                    if (target >= 0) topIndex = target
                                                }
                                            }

                                            is TopItem.AllApps -> {
                                                val isAlready = rawSelectedTopItem is TopItem.AllApps
                                                if (isAlready) {
                                                    allAppsOpen = true
                                                    allAppsSelectedIndex =
                                                        allAppsSelectedIndex.coerceIn(0, allAppsLastIndex.coerceAtLeast(0))
                                                } else {
                                                    val target = realItems.indexOfFirst { it is TopItem.AllApps }
                                                    if (target >= 0) topIndex = target
                                                }
                                            }

                                            else -> Unit
                                        }
                                    }
                                },
                                onLongPress = { item ->
                                    when (item) {
                                        is TopItem.App -> openMenuFor(item.app)
                                        else -> Unit
                                    }
                                },
                                onSelectionTick = {
                                    if (vibrationEnabled) Haptics.tick(context)
                                }
                            )

                            Spacer(Modifier.height(18.dp))
                            Spacer(Modifier.height(18.dp))

                            if (showBigAppName) {
                                Text(
                                    text = when (val it = selectedTopItem) {
                                        is TopItem.App -> it.app.label
                                        is TopItem.AllApps -> context.getString(R.string.homescreen_apps_title)
                                        else -> ""
                                    },
                                    color = Color.White,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 44.sp
                                )

                                Spacer(Modifier.height(14.dp))
                            } else {
                                Spacer(Modifier.height(6.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HomeActions(
                                    canLaunch = selectedTopItem is TopItem.App,
                                    playFR = playFR,
                                    menuFR = menuFR,
                                    onPlay = {
                                        if (vibrationEnabled) Haptics.click(context)
                                        (selectedTopItem as? TopItem.App)?.app?.let { launchApp(it) }
                                    },
                                    onMenu = {
                                        if (vibrationEnabled) Haptics.click(context)
                                        if (selectedTopItem is TopItem.App) {
                                            openMenuFor((selectedTopItem as TopItem.App).app)
                                        }
                                    }
                                )

                                Spacer(Modifier.width(18.dp))

                                AnimatedVisibility(
                                    visible = (homeSection == HomeSection.WIDGETS),
                                    modifier = Modifier
                                        .weight(1f)
                                        .wrapContentSize(Alignment.Center),
                                    enter = fadeIn(animationSpec = tween(180)),
                                    exit = fadeOut(animationSpec = tween(180))
                                ) {
                                    BottomPanelTabsRow(active = bottomPanel)
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                        }

                        item {
                            val panelFR = currentPanelFR()

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .focusRequester(panelFR)
                                    .focusable()
                            ) {
                                AnimatedContent(
                                    targetState = bottomPanel,
                                    transitionSpec = {
                                        val order = listOf(BottomPanel.WIDGETS, BottomPanel.CALENDAR, BottomPanel.MUSIC)
                                        val from = order.indexOf(initialState).coerceAtLeast(0)
                                        val to = order.indexOf(targetState).coerceAtLeast(0)
                                        val dir = if (to > from) 1 else -1

                                        (slideInHorizontally(
                                            animationSpec = tween(220),
                                            initialOffsetX = { full -> full * dir }
                                        ) + fadeIn(tween(220)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    animationSpec = tween(220),
                                                    targetOffsetX = { full -> -full * dir }
                                                ) + fadeOut(tween(180))
                                            )
                                    },
                                    label = "bottomPanel"
                                ) { p ->
                                    when (p) {
                                        BottomPanel.WIDGETS -> {
                                            WidgetsPanel(
                                                placements = placements,
                                                grid = gridSpec,
                                                focusRequester = widgetsFR,
                                                registerKeyHandler = { handler -> widgetsKeyHandler = handler },
                                                onRequestAddAt = { x, y -> requestAddAt(x, y) },
                                                onMove = { placement -> moveWidgetClockwise(placement) },
                                                onDelete = { placement -> deleteWidget(placement) },
                                                onCellPxKnown = { px -> cellPx = px },
                                                renderWidget = { placement, modifier ->
                                                    val info = widgetManager.getAppWidgetInfo(placement.appWidgetId)
                                                    if (info == null) {
                                                        Box(modifier, contentAlignment = Alignment.Center) {
                                                            Text(
                                                                context.getString(R.string.homescreen_widget_error_title),
                                                                color = Color.White.copy(alpha = 0.65f)
                                                            )
                                                        }
                                                    } else {
                                                        AndroidView(
                                                            factory = { ctx ->
                                                                try {
                                                                    widgetHost.createView(ctx, placement.appWidgetId, info).apply {
                                                                        setAppWidget(placement.appWidgetId, info)
                                                                        setPadding(0, 0, 0, 0)
                                                                        layoutParams = FrameLayout.LayoutParams(
                                                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                                                            FrameLayout.LayoutParams.MATCH_PARENT
                                                                        )
                                                                    }
                                                                } catch (_: Throwable) {
                                                                    FrameLayout(ctx).apply {
                                                                        addView(
                                                                            TextView(ctx).apply {
                                                                                text = context.getString(R.string.homescreen_widget_error_title)
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            },
                                                            update = { view ->
                                                                view.setPadding(0, 0, 0, 0)
                                                                view.layoutParams = FrameLayout.LayoutParams(
                                                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                                                    FrameLayout.LayoutParams.MATCH_PARENT
                                                                )
                                                            },
                                                            modifier = modifier
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        BottomPanel.CALENDAR -> {
                                            CalendarPanelCard(
                                                modifier = Modifier.fillMaxSize(),
                                                vibrationEnabled = vibrationEnabled,
                                                registerKeyHandler = { handler -> calendarKeyHandler = handler }
                                            )
                                        }

                                        BottomPanel.MUSIC -> {
                                            MusicControlPanelCard(
                                                modifier = Modifier.fillMaxSize(),
                                                registerKeyHandler = { handler -> musicKeyHandler = handler },
                                                focusRequester = musicFR
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(28.dp))
                        }
                    }
                }
            }
        }

        if (tab == Tab.GAMES) {
            val menuAppSnapshot = menuTargetApp

            val canUninstallMenuApp = remember(menuAppSnapshot?.packageName) {
                menuAppSnapshot?.packageName?.let(::canUninstall) == true
            }
            val canDisableMenuApp = remember(menuAppSnapshot?.packageName) {
                menuAppSnapshot?.packageName?.let(::canDisable) == true
            }

            HomeOverlays(
                searchOpen = searchOpen,
                searchQuery = searchQuery,
                searchResults = remember(allApps, searchQuery) {
                    val q = searchQuery.trim()
                    if (q.isBlank()) emptyList()
                    else allApps.filter { it.label.contains(q, true) }.take(20)
                },
                menuOpen = menuOpen,
                isPinned = menuAppSnapshot?.packageName in pinned,
                canUninstall = canUninstallMenuApp,
                canDisable = canDisableMenuApp,
                onSearchChange = { searchQuery = it },
                onSearchClose = { searchOpen = false; searchQuery = "" },
                onSearchLaunch = {
                    searchOpen = false
                    searchQuery = ""
                    launchApp(it)
                },
                onTogglePin = {
                    val app = menuAppSnapshot ?: return@HomeOverlays
                    closeMenu()
                    scope.launch { repo.setPinned(app.packageName, value = app.packageName !in pinned) }
                },
                onAppInfo = {
                    val app = menuAppSnapshot ?: return@HomeOverlays
                    closeMenu()
                    openAppInfo(app)
                },
                onUninstall = {
                    val app = menuAppSnapshot ?: return@HomeOverlays
                    closeMenu()
                    requestUninstall(app)
                },
                onDisable = {
                    val app = menuAppSnapshot ?: return@HomeOverlays
                    closeMenu()
                    openAppInfo(app)
                },
                onMenuClose = { closeMenu() }
            )
        }

        // ✅ Widget picker overlay: biztos fókusz + saját key handler
        val widgetPickerFR = remember { FocusRequester() }

        AnimatedVisibility(
            visible = showWidgetPicker,
            enter = fadeIn(animationSpec = tween(160)) +
                    scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(130)) +
                    scaleOut(targetScale = 0.94f, animationSpec = tween(170))
        ) {
            // FONTOS: itt van kompozícióban a Card, ezért itt kérünk fókuszt
            LaunchedEffect(Unit) {
                // 1 frame várás, hogy biztosan felépüljön a focus node
                kotlinx.coroutines.yield()
                widgetPickerFR.requestFocus()
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(1f)
                        .fillMaxHeight(1f)
                        .focusRequester(widgetPickerFR)
                        .focusable()
                ) {
                    WidgetPickerScreen(
                        state = pickerState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberQuickTileClickHandler(context: Context): (QuickTileType) -> Unit {
    var torchOn by remember { mutableStateOf(false) }
    var pendingAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun toast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun log(msg: String) {
        android.util.Log.d("PX5QS", msg)
    }

    fun openIntent(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openInternetPanel() = openIntent(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
    fun openBluetoothSettings() = openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun openDndAccessSettings() = openIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    fun openDisplaySettings() = openIntent(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    fun openSystemSettings() = openIntent(Intent(Settings.ACTION_SETTINGS))

    fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        log("CAMERA permission result: $granted")
        if (granted) {
            pendingAfterPermission?.invoke()
        } else {
            openAppDetailsSettings()
        }
        pendingAfterPermission = null
    }

    val requestBtConnect = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        log("BT_CONNECT permission result: $granted")
        if (granted) {
            pendingAfterPermission?.invoke()
        } else {
            openAppDetailsSettings()
        }
        pendingAfterPermission = null
    }

    fun toggleBluetooth() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            openBluetoothSettings()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perm = android.Manifest.permission.BLUETOOTH_CONNECT
            val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingAfterPermission = { toggleBluetooth() }
                requestBtConnect.launch(perm)
                return
            }
        }

        val ok = runCatching {
            if (adapter.isEnabled) adapter.disable() else adapter.enable()
        }.getOrNull() == true

        if (!ok) openBluetoothSettings()
    }

    fun toggleFlashlightInternal() {
        val pm = context.packageManager
        val hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        log("toggleFlashlightInternal hasFlash=$hasFlash")
        if (!hasFlash) {
            toast(context.getString(R.string.homescreen_qs_no_flashlight))
            return
        }

        val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraId = runCatching {
            cam.cameraIdList.firstOrNull { id ->
                val ch = cam.getCameraCharacteristics(id)
                ch.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()

        log("toggleFlashlightInternal cameraId=$cameraId")
        if (cameraId == null) {
            toast(context.getString(R.string.homescreen_qs_no_flash_camera))
            return
        }

        val next = !torchOn
        val ok = runCatching {
            cam.setTorchMode(cameraId, next)
            torchOn = next
        }.isSuccess

        log("toggleFlashlightInternal setTorchMode next=$next ok=$ok")
        if (!ok) {
            toast(context.getString(R.string.homescreen_qs_flashlight_toggle_failed))
        }
    }

    fun toggleFlashlight() {
        val perm = android.Manifest.permission.CAMERA
        val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        log("toggleFlashlight permissionGranted=$granted")
        if (!granted) {
            pendingAfterPermission = { toggleFlashlightInternal() }
            requestCamera.launch(perm)
            return
        }
        toggleFlashlightInternal()
    }

    fun toggleDnd() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            openDndAccessSettings()
            return
        }

        val cur = nm.currentInterruptionFilter
        val next = if (cur == NotificationManager.INTERRUPTION_FILTER_NONE) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_NONE
        }

        runCatching { nm.setInterruptionFilter(next) }
            .onFailure { openDndAccessSettings() }
    }

    return remember {
        { type: QuickTileType ->
            log("Tile click: $type")
            when (type) {
                QuickTileType.WIFI -> openInternetPanel()
                QuickTileType.BT -> toggleBluetooth()
                QuickTileType.FLASHLIGHT -> toggleFlashlight()
                QuickTileType.DND -> toggleDnd()
                QuickTileType.ROTATION -> openDisplaySettings()
                QuickTileType.AIRPLANE -> openSystemSettings()
                QuickTileType.LOCATION -> openIntent(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                QuickTileType.STB -> openSystemSettings()
            }
        }
    }
}