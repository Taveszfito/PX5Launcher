@file:OptIn(ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")

package com.dueboysenberry1226.px5launcher.ui.theme

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.LaunchableApp
import com.dueboysenberry1226.px5launcher.data.SettingsRepository
import com.dueboysenberry1226.px5launcher.data.NotificationsRepository
import com.dueboysenberry1226.px5launcher.data.QuickSettingsRepository
import com.dueboysenberry1226.px5launcher.data.Tab
import com.dueboysenberry1226.px5launcher.data.WidgetGridSpec
import com.dueboysenberry1226.px5launcher.data.WidgetLayoutMode
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import com.dueboysenberry1226.px5launcher.data.WidgetsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
fun PSHomeRoute(
    pm: PackageManager,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val settingsRepo = remember(context) { SettingsRepository(context) }
    val is24h by settingsRepo.clock24hFlow.collectAsState(initial = true)
    val showBigAppName by settingsRepo.showBigAppNameFlow.collectAsState(initial = true)
    val quickTileClick = rememberQuickTileClickHandler(context)
    val accentFromIcon by settingsRepo.accentFromAppIconFlow.collectAsState(initial = true)
    val vibrationEnabled by settingsRepo.vibrationEnabledFlow.collectAsState(initial = true)
    val allAppsColumns by settingsRepo.allAppsColumnsFlow.collectAsState(initial = 4)

    val derived = rememberPSHomeDerivedData(
        context = context,
        pm = pm,
        accentFromIcon = accentFromIcon,
        allAppsColumns = allAppsColumns
    )

    val liveNotifs = NotificationsRepository.live.collectAsState().value
    val historyNotifs = NotificationsRepository.history.collectAsState().value
    val qsState = QuickSettingsRepository.state.collectAsState().value

    var notifHistoryMode by rememberSaveable { mutableStateOf(false) }
    var notifEnterFocusTick by rememberSaveable { mutableIntStateOf(0) }
    var notifUpFromLeftButtonsAllowed by rememberSaveable { mutableStateOf(false) }
    var notifUpFromQsTopRowAllowed by rememberSaveable { mutableStateOf(false) }
    val notifUpToTopbarAllowed = notifUpFromLeftButtonsAllowed || notifUpFromQsTopRowAllowed

    var mediaKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
    var widgetsKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
    var calendarKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
    var musicKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }
    var notifKeyHandler by remember { mutableStateOf<((KeyEvent) -> Boolean)?>(null) }

    val widgetsRepo = remember(context) { WidgetsRepository(context) }
    val widgetHost = remember(context) { AppWidgetHost(context, PS_HOME_WIDGET_HOST_ID) }
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }

    DisposableEffect(Unit) {
        widgetHost.startListening()
        onDispose { widgetHost.stopListening() }
    }

    val gridSpec = remember { WidgetGridSpec(cols = 8, rows = 2) }
    val landscapePlacements = derived.placements

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
        scope.launch { derived.homeScrollState.animateScrollToItem(0) }
    }

    fun goWidgets() {
        scope.launch {
            derived.homeScrollState.animateScrollToItem(1)
            yield()
            currentPanelFR().requestFocus()
        }
    }

    fun goActions() {
        scope.launch {
            derived.homeScrollState.animateScrollToItem(0)
            yield()
            focusActions()
        }
    }

    fun setBottomPanel(next: BottomPanel) {
        if (bottomPanel == next) return
        if (next != BottomPanel.WIDGETS) widgetsKeyHandler = null
        bottomPanel = next
    }

    val bottomPanelPositions = remember {
        listOf(
            BottomPanelPosition(BottomPanel.WIDGETS, col = 0, row = 0),
            BottomPanelPosition(BottomPanel.CALENDAR, col = 1, row = 0),
            BottomPanelPosition(BottomPanel.MUSIC, col = 2, row = 0),
        )
    }

    fun adjacentBottomPanel(from: BottomPanel, dir: Int): BottomPanel? {
        val current = bottomPanelPositions.firstOrNull { it.panel == from } ?: return null
        return bottomPanelPositions
            .asSequence()
            .filter { it.row == current.row }
            .filter { if (dir < 0) it.col < current.col else it.col > current.col }
            .minByOrNull { kotlin.math.abs(it.col - current.col) }
            ?.panel
    }

    var pendingBottomPanelFocus by remember { mutableStateOf<BottomPanel?>(null) }

    fun moveBottomPanelHorizontal(dir: Int): Boolean {
        val next = adjacentBottomPanel(bottomPanel, dir) ?: return false
        if (next == bottomPanel) return false
        pendingBottomPanelFocus = next
        setBottomPanel(next)
        return true
    }

    LaunchedEffect(bottomPanel, pendingBottomPanelFocus) {
        val target = pendingBottomPanelFocus ?: return@LaunchedEffect
        if (target != bottomPanel) return@LaunchedEffect
        yield()
        yield()
        when (target) {
            BottomPanel.WIDGETS -> widgetsFR
            BottomPanel.CALENDAR -> calendarFR
            BottomPanel.MUSIC -> musicFR
        }.requestFocus()
        pendingBottomPanelFocus = null
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

    LaunchedEffect(allAppsOpen) {
        if (allAppsOpen) {
            derived.bumpAppsRefreshTick()
            allAppsSelectedIndex = allAppsSelectedIndex.coerceIn(-2, (derived.allApps.size - 1).coerceAtLeast(0))
            if (allAppsSelectedIndex == -2) allAppsSelectedIndex = 0
        }
    }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 52.dp.toPx() }
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
    var showWidgetPicker by rememberSaveable { mutableStateOf(false) }
    var pendingPickCellX by remember { mutableIntStateOf(0) }
    var pendingPickCellY by remember { mutableIntStateOf(0) }

    fun showToastWidget(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun finalizeAddWidget(appWidgetId: Int, info: AppWidgetProviderInfo, cellX: Int, cellY: Int) {
        val pend = pendingAdd
        val sx = pend?.spanX ?: 2
        val sy = pend?.spanY ?: 2

        if (!canPlaceAtWidget(gridSpec, landscapePlacements, cellX, cellY, sx, sy)) {
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
                    spanY = sy,
                    layoutMode = WidgetLayoutMode.LANDSCAPE
                )
            )
        }
        pendingAdd = null
    }

    rememberLauncherForActivityResult(
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
        finalizeAddWidget(appWidgetId, info, cellX, cellY)
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

    val cellDp = remember(cellPx) {
        if (cellPx > 0f) with(density) { cellPx.toDp() } else 90.dp
    }

    val pickerState = rememberWidgetPickerState(
        pm = pm,
        appWidgetManager = widgetManager,
        cellWidthDp = cellDp,
        cellHeightDp = cellDp,
        cellGapXDp = 12.dp,
        cellGapYDp = 12.dp,
        maxSpanX = 2,
        maxSpanY = 2,
        filterOutOversize = true,
        onPick = { providerInfo, sx, sy ->
            if (vibrationEnabled) Haptics.click(context)

            val provider = providerInfo.provider ?: run {
                showToastWidget(context.getString(R.string.homescreen_widget_provider_null))
                return@rememberWidgetPickerState
            }

            val anchor = resolveAnchorForTap(
                gridSpec = gridSpec,
                placements = landscapePlacements,
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
            showWidgetPicker = false
        }
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
            gridSpec = gridSpec,
            placements = landscapePlacements,
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

    BackHandler(enabled = true) {
        when {
            showWidgetPicker -> showWidgetPicker = false
            menuOpen -> {
                menuOpen = false
                menuTargetApp = null
            }
            searchOpen -> {
                searchOpen = false
                searchQuery = ""
            }
            allAppsOpen -> allAppsOpen = false
            else -> Unit
        }
    }

    fun scrollSelectedToAnchor(animated: Boolean) {
        if (derived.topItems.isEmpty()) return
        val idx = (topIndex + derived.anchorIndex).coerceIn(0, derived.topItems.lastIndex)
        scope.launch {
            if (animated) derived.stripState.animateScrollToItem(idx)
            else derived.stripState.scrollToItem(idx)
        }
    }

    LaunchedEffect(derived.topItems.size) { scrollSelectedToAnchor(animated = false) }
    LaunchedEffect(topIndex) { scrollSelectedToAnchor(animated = true) }

    fun stepStripByController(delta: Int) {
        if (delta == 0) return
        val next = (topIndex + delta).coerceIn(0, derived.realLastIndex)
        if (next == topIndex) return
        topIndex = next
    }

    val keyHandler = buildPSHomeKeyHandler(
        context = context,
        scope = scope,
        vibrationEnabled = vibrationEnabled,
        tabOrder = derived.tabOrder,
        okCodes = derived.okCodes,
        homeSection = homeSection,
        tab = tab,
        topBarIndex = topBarIndex,
        allAppsOpen = allAppsOpen,
        allApps = derived.allApps,
        allAppsLastIndex = derived.allAppsLastIndex,
        allAppsSelectedIndex = allAppsSelectedIndex,
        columns = derived.columns,
        notifUpToTopbarAllowed = notifUpToTopbarAllowed,
        selectedTopItem = derived.selectedTopItem(topIndex, homeSection),
        selectedApp = derived.selectedApp(topIndex, homeSection),
        pinned = derived.pinned,
        actionIndex = actionIndex,
        bottomPanel = bottomPanel,
        notifEnterFocusTick = notifEnterFocusTick,
        notifKeyHandler = notifKeyHandler,
        widgetsKeyHandler = widgetsKeyHandler,
        calendarKeyHandler = calendarKeyHandler,
        musicKeyHandler = musicKeyHandler,
        onOpenSettings = onOpenSettings,
        focusManager = focusManager,
        moveBottomPanelHorizontal = ::moveBottomPanelHorizontal,
        stepStripByController = ::stepStripByController,
        goActions = ::goActions,
        goWidgets = ::goWidgets,
        goHomeTopKeepScroll = ::goHomeTopKeepScroll,
        focusActions = ::focusActions,
        setTab = { tab = it },
        setTopBarIndex = { topBarIndex = it },
        setHomeSection = { homeSection = it },
        setAllAppsOpen = { allAppsOpen = it },
        setAllAppsSelectedIndex = { allAppsSelectedIndex = it },
        setActionIndex = { actionIndex = it },
        setSearchOpen = { searchOpen = it },
        searchOpen = searchOpen,
        setSearchQuery = { searchQuery = it },
        setNotifEnterFocusTick = { notifEnterFocusTick = it },
        launchApp = { app -> launchPsHomeApp(context, pm, scope, derived.repo, app) },
        togglePin = { app -> scope.launch { derived.repo.setPinned(app.packageName, value = app.packageName !in derived.pinned) } },
        openMenuFor = { app -> if (app != null) { menuTargetApp = app; menuOpen = true } },
        closeMenu = { menuOpen = false; menuTargetApp = null }
    )

    PSHomeMainBox(
        context = context,
        bg = derived.bg,
        swipeThresholdPx = swipeThresholdPx,
        showWidgetPicker = showWidgetPicker,
        tab = tab,
        homeSection = homeSection,
        bottomPanel = bottomPanel,
        menuOpen = menuOpen,
        searchOpen = searchOpen,
        allAppsOpen = allAppsOpen,
        vibrationEnabled = vibrationEnabled,
        keyHandler = keyHandler,
        handlePickerKey = { pickerState.handleKey(it) },
        handleMediaKey = { mediaKeyHandler?.invoke(it) == true },
        onCloseWidgetPicker = { showWidgetPicker = false },
        onBackConsumed = { true },
        onStepStripByController = ::stepStripByController,
        onMoveBottomPanelHorizontal = ::moveBottomPanelHorizontal,
        onGoHomeTopKeepScroll = ::goHomeTopKeepScroll,
        onGoWidgets = { homeSection = HomeSection.WIDGETS; goWidgets() },
        onSwipeToTop = {
            if (homeSection != HomeSection.TOP) {
                homeSection = HomeSection.TOP
                focusManager.clearFocus(force = true)
                goHomeTopKeepScroll()
            }
        },
        focusManager = focusManager,
        topBarIndex = topBarIndex,
        is24h = is24h,
        onSetTab = { tab = it },
        onSetSearchOpen = { searchOpen = it },
        onOpenSettings = onOpenSettings,
        body = {
            if (tab == Tab.MEDIA) {
                MediaRoute(
                    onRequestBackToGames = { tab = Tab.GAMES },
                    hubSelectionEnabled = (homeSection != HomeSection.TOPBAR),
                    registerKeyHandler = { handler -> mediaKeyHandler = handler },
                    vibrationEnabled = vibrationEnabled
                )
            } else if (tab == Tab.NOTIFICATIONS) {
                NotificationsTabScreen(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    liveNotifications = liveNotifs,
                    historyNotifications = historyNotifs,
                    enterFocusTick = notifEnterFocusTick,
                    historyMode = notifHistoryMode,
                    onLeftButtonsFocusEdgeChanged = { notifUpFromLeftButtonsAllowed = it },
                    onQsTopRowFocusEdgeChanged = { notifUpFromQsTopRowAllowed = it },
                    registerKeyHandler = { notifKeyHandler = it },
                    onRequestMoveToTopbar = {
                        homeSection = HomeSection.TOPBAR
                        topBarIndex = when (tab) {
                            Tab.GAMES -> 0
                            Tab.MEDIA -> 1
                            Tab.NOTIFICATIONS -> 2
                        }
                        focusManager.clearFocus(force = true)
                    },
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
                PSHomeGamesBody(
                    topData = derived,
                    homeSection = homeSection,
                    allAppsOpen = allAppsOpen,
                    allAppsSelectedIndex = allAppsSelectedIndex,
                    topIndex = topIndex,
                    showBigAppName = showBigAppName,
                    vibrationEnabled = vibrationEnabled,
                    actionIndex = actionIndex,
                    bottomPanel = bottomPanel,
                    playFR = playFR,
                    menuFR = menuFR,
                    widgetsFR = widgetsFR,
                    calendarFR = calendarFR,
                    musicFR = musicFR,
                    widgetHost = widgetHost,
                    widgetManager = widgetManager,
                    gridSpec = gridSpec,
                    placements = landscapePlacements,
                    onSetAllAppsSelectedIndex = { allAppsSelectedIndex = it },
                    onSetAllAppsOpen = { allAppsOpen = it },
                    onSetTopIndex = { topIndex = it },
                    onSetHomeSection = { homeSection = it },
                    onSetWidgetsKeyHandler = { widgetsKeyHandler = it },
                    onSetCalendarKeyHandler = { calendarKeyHandler = it },
                    onSetMusicKeyHandler = { musicKeyHandler = it },
                    onSetCellPx = { cellPx = it },
                    onRequestAddAt = ::requestAddAt,
                    onMoveWidgetClockwise = ::moveWidgetClockwise,
                    onDeleteWidget = ::deleteWidget,
                    onGoHomeTopKeepScroll = ::goHomeTopKeepScroll,
                    onOpenMenuFor = { app -> if (app != null) { menuTargetApp = app; menuOpen = true } },
                    onLaunchApp = { app -> launchPsHomeApp(context, pm, scope, derived.repo, app) },
                    onGoActions = ::goActions,
                    currentPanelFR = ::currentPanelFR
                )
            }
        },
        overlays = {
            if (tab == Tab.GAMES) {
                val menuAppSnapshot = menuTargetApp
                val canUninstallMenuApp = remember(menuAppSnapshot?.packageName) {
                    menuAppSnapshot?.packageName?.let { canPsHomeUninstall(context, pm, it) } == true
                }
                val canDisableMenuApp = remember(menuAppSnapshot?.packageName) {
                    menuAppSnapshot?.packageName?.let { canPsHomeDisable(context, pm, it) } == true
                }

                PSHomeGamesOverlays(
                    searchOpen = searchOpen,
                    searchQuery = searchQuery,
                    searchResults = derived.allApps,
                    menuOpen = menuOpen,
                    isPinned = menuAppSnapshot?.packageName in derived.pinned,
                    canUninstall = canUninstallMenuApp,
                    canDisable = canDisableMenuApp,
                    onSearchChange = { searchQuery = it },
                    onSearchClose = { searchOpen = false; searchQuery = "" },
                    onSearchLaunch = { searchOpen = false; searchQuery = ""; launchPsHomeApp(context, pm, scope, derived.repo, it) },
                    onTogglePin = {
                        val app = menuAppSnapshot ?: return@PSHomeGamesOverlays
                        menuOpen = false
                        menuTargetApp = null
                        scope.launch { derived.repo.setPinned(app.packageName, value = app.packageName !in derived.pinned) }
                    },
                    onAppInfo = {
                        val app = menuAppSnapshot ?: return@PSHomeGamesOverlays
                        menuOpen = false
                        menuTargetApp = null
                        openPsHomeAppInfo(context, app)
                    },
                    onUninstall = {
                        val app = menuAppSnapshot ?: return@PSHomeGamesOverlays
                        menuOpen = false
                        menuTargetApp = null
                        requestPsHomeUninstall(context, pm, app, ::openPsHomeAppInfo)
                    },
                    onDisable = {
                        val app = menuAppSnapshot ?: return@PSHomeGamesOverlays
                        menuOpen = false
                        menuTargetApp = null
                        openPsHomeAppInfo(context, app)
                    },
                    onMenuClose = { menuOpen = false; menuTargetApp = null }
                )
            }

            PSHomeWidgetPickerOverlay(showWidgetPicker = showWidgetPicker) {
                WidgetPickerScreen(state = pickerState, modifier = Modifier.fillMaxSize())
            }
        }
    )
}