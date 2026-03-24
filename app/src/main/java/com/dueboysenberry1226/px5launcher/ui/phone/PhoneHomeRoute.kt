@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone


import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.LauncherRepository
import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import com.dueboysenberry1226.px5launcher.data.SettingsRepository
import com.dueboysenberry1226.px5launcher.data.WidgetLayoutMode
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import com.dueboysenberry1226.px5launcher.data.WidgetsRepository
import com.dueboysenberry1226.px5launcher.ui.theme.PhoneGlass
import com.dueboysenberry1226.px5launcher.ui.theme.PhoneWallpaperGlassState
import com.dueboysenberry1226.px5launcher.ui.theme.ProvidePhoneWallpaperGlassState
import com.dueboysenberry1226.px5launcher.ui.theme.WallpaperGlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val WIDGET_HOST_ID = 1024
private const val PHONE_HOME_PAGE_COUNT = 4
private const val PHONE_HOME_SWIPE_SWITCH_THRESHOLD_PX = 180f
private const val PHONE_HOME_EDGE_SWITCH_THRESHOLD_PX = 42f
private const val PHONE_HOME_EDGE_SWITCH_COOLDOWN_MS = 420L

@Composable
private fun PhoneHomePageDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until pageCount) {
            val active = i == currentPage
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (active) {
                            Color.White.copy(alpha = 0.95f)
                        } else {
                            Color.White.copy(alpha = 0.34f)
                        }
                    )
            )
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PhoneHomeRoute(
    pm: PackageManager
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT
    val scopeLocale = remember { Locale.getDefault() }
    val scope = rememberCoroutineScope()

    val repo = remember(pm, context) { LauncherRepository(context, pm) }
    val recents by repo.recentsFlow.collectAsState(initial = emptyList())

    val widgetsRepo = remember(context) { WidgetsRepository(context) }
    val settingsRepo = remember(context) { SettingsRepository(context) }

    val currentWidgetLayoutMode = remember(isPortrait) {
        if (isPortrait) WidgetLayoutMode.PORTRAIT else WidgetLayoutMode.LANDSCAPE
    }

    val widgets by widgetsRepo
        .widgetsFlow(currentWidgetLayoutMode)
        .collectAsState(initial = emptyList())

    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val appWidgetHost = remember(context) { AppWidgetHost(context, WIDGET_HOST_ID) }

    DisposableEffect(Unit) {
        appWidgetHost.startListening()
        onDispose { appWidgetHost.stopListening() }
    }

    val ambientColor = remember { mutableStateOf(Color(0xFF101826)) }

    val wallpaperUri by settingsRepo.phoneHomeWallpaperUriFlow.collectAsState(initial = null)
    val wallpaperScale by settingsRepo.phoneHomeWallpaperScaleFlow.collectAsState(initial = 1f)
    val wallpaperOffsetX by settingsRepo.phoneHomeWallpaperOffsetXFlow.collectAsState(initial = 0f)
    val wallpaperOffsetY by settingsRepo.phoneHomeWallpaperOffsetYFlow.collectAsState(initial = 0f)
    val wallpaperApplyHome by settingsRepo.phoneHomeWallpaperApplyHomeFlow.collectAsState(initial = true)

    val hasHomeWallpaper = !wallpaperUri.isNullOrBlank() && wallpaperApplyHome

    val baseBg = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0B1020),
                Color(0xFF070A12),
                Color(0xFF05070D)
            )
        )
    }

    val ambientOverlay = remember(ambientColor.value) {
        Brush.verticalGradient(
            listOf(
                ambientColor.value.copy(alpha = 0.28f),
                ambientColor.value.copy(alpha = 0.12f),
                Color.Transparent
            )
        )
    }

    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.Default) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0)
                .map { ri ->
                    AppItem(
                        label = ri.loadLabel(pm).toString(),
                        packageName = ri.activityInfo.packageName,
                        icon = ri.loadIcon(pm)
                    )
                }
                .sortedBy { it.label.lowercase(scopeLocale) }
        }
    }

    fun launch(pkg: String) {
        val i = pm.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        scope.launch { repo.pushRecent(pkg) }
    }

    LaunchedEffect(recents, allApps, hasHomeWallpaper) {
        if (hasHomeWallpaper) {
            ambientColor.value = Color(0xFF101826)
            return@LaunchedEffect
        }

        val pkg = recents.firstOrNull() ?: return@LaunchedEffect
        val app = allApps.firstOrNull { it.packageName == pkg }
        setAmbientFromApp(ambientColor, app)
    }

    val storedSlotPages by repo.phoneHomeSlotsPagesFlow.collectAsState(
        initial = List(PHONE_HOME_PAGE_COUNT) { emptyList<String?>() }
    )
    val storedCards by repo.phoneHomeCardsFlow.collectAsState(initial = emptyList())

    var slotPages by remember { mutableStateOf<List<List<String?>>>(emptyList()) }
    val cards = remember { mutableStateListOf<PhoneCardPlacement>() }

    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    val pageDragOffsetPx = remember { Animatable(0f) }

    fun pageTemplate(): List<String?> =
        normalizeSlots(emptyList())

    fun normalizedPage(page: List<String?>): List<String?> =
        normalizeSlots(page)

    fun normalizedPages(input: List<List<String?>>): List<List<String?>> {
        val out = input.map { normalizedPage(it) }.toMutableList()
        while (out.size < PHONE_HOME_PAGE_COUNT) {
            out.add(pageTemplate())
        }
        return out.take(PHONE_HOME_PAGE_COUNT)
    }

    fun ensurePageStorage() {
        if (slotPages.size < PHONE_HOME_PAGE_COUNT) {
            slotPages = normalizedPages(slotPages)
        }
    }

    fun persistSlots() {
        val snap = normalizedPages(slotPages)
        slotPages = snap
        scope.launch { repo.savePhoneHomeSlotsPages(snap) }
    }

    fun persistCards() {
        val snap = cards.toList()
        scope.launch { repo.savePhoneHomeCards(snap) }
    }

    fun slotsForPage(pageIndex: Int): List<String?> {
        ensurePageStorage()
        return slotPages[pageIndex.coerceIn(0, PHONE_HOME_PAGE_COUNT - 1)]
    }

    fun cardsForPage(pageIndex: Int): List<PhoneCardPlacement> =
        cards.filter { it.pageIndex == pageIndex }

    fun widgetsForPage(pageIndex: Int): List<WidgetPlacement> =
        widgets.filter { it.pageIndex == pageIndex }



    LaunchedEffect(storedSlotPages) {
        val normPages = normalizedPages(storedSlotPages)

        slotPages = normPages
        currentPage = currentPage.coerceIn(0, PHONE_HOME_PAGE_COUNT - 1)

        if (storedSlotPages.size in 1 until PHONE_HOME_PAGE_COUNT) {
            persistSlots()
        }
    }

    LaunchedEffect(storedCards) {
        cards.clear()
        cards.addAll(storedCards)
    }

    LaunchedEffect(allApps, slotPages) {
        if (allApps.isEmpty() || slotPages.isEmpty()) return@LaunchedEffect

        val valid = allApps.asSequence().map { it.packageName }.toHashSet()
        var changed = false

        val rebuiltPages = slotPages.map { page ->
            val rebuilt = normalizeSlots(page).toMutableList()
            for (i in rebuilt.indices) {
                val id = rebuilt[i]
                if (id != null && id !in valid) {
                    rebuilt[i] = null
                    changed = true
                }
            }
            rebuilt.toList()
        }

        if (changed) {
            slotPages = rebuiltPages
            persistSlots()
        }
    }

    fun resolveLabelForSlot(id: String?): String {
        if (id == null) return ""
        return allApps.firstOrNull { it.packageName == id }?.label ?: id
    }

    fun resolveIconForSlot(id: String?) =
        if (id == null) null else allApps.firstOrNull { it.packageName == id }?.icon

    fun removeFromHome(id: String) {
        var changed = false
        val rebuiltPages = slotPages.map { page ->
            val rebuilt = page.toMutableList()
            for (i in rebuilt.indices) {
                if (rebuilt[i] == id) {
                    rebuilt[i] = null
                    changed = true
                }
            }
            normalizeSlots(rebuilt)
        }

        if (changed) {
            slotPages = rebuiltPages
            persistSlots()
        }
    }

    var placeError by remember { mutableStateOf<String?>(null) }
    fun clearPlaceError() {
        placeError = null
    }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var drawerDragActive by remember { mutableStateOf(false) }

    var homeQuickMenuOpen by remember { mutableStateOf(false) }
    var addStuffOpen by remember { mutableStateOf(false) }
    var addStuffTab by remember { mutableIntStateOf(0) }
    var showWallpaperPicker by rememberSaveable { mutableStateOf(false) }
    var selectedCard by remember { mutableStateOf<PhoneCardType?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    var suppressBackgroundLongPress by remember { mutableStateOf(false) }

    var editMode by rememberSaveable { mutableStateOf(false) }
    fun exitEditMode() {
        editMode = false
        clearPlaceError()
    }

    var dragging by remember { mutableStateOf<DragPayload?>(null) }
    var hasDragPointer by remember { mutableStateOf(false) }
    var dragPointerId by remember { mutableStateOf<PointerId?>(null) }
    var dropPreview by remember { mutableStateOf<DropPreview?>(null) }

    var gridTopLeftPx by remember { mutableStateOf(Offset.Zero) }
    var cellSizePx by remember { mutableFloatStateOf(0f) }
    var gapHPx by remember { mutableFloatStateOf(0f) }
    var gapVPx by remember { mutableFloatStateOf(0f) }
    var rowsToShowState by remember { mutableIntStateOf(1) }
    var gridUsedWidth by remember { mutableStateOf(0.dp) }
    var routeRootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var lastEdgePageTurnAt by remember { mutableLongStateOf(0L) }

    fun stopDrag() {
        dragging = null
        hasDragPointer = false
        dropPreview = null
        lastEdgePageTurnAt = 0L
    }

    fun setPage(newPage: Int) {
        val clamped = newPage.coerceIn(0, PHONE_HOME_PAGE_COUNT - 1)
        if (clamped == currentPage) return
        currentPage = clamped
        dropPreview = null
        clearPlaceError()
    }

    fun cellFromPointer(pointerPx: Offset): Pair<Int, Int>? {
        val localX = pointerPx.x - gridTopLeftPx.x
        val localY = pointerPx.y - gridTopLeftPx.y
        if (localX < 0f || localY < 0f) return null

        val stepX = cellSizePx + gapHPx
        val stepY = cellSizePx + gapVPx

        val col = (localX / stepX).toInt()
        val row = (localY / stepY).toInt()

        if (col !in 0 until COLS) return null
        if (row !in 0 until rowsToShowState) return null

        return col to row
    }

    fun isCellCoveredByCard(
        pageIndex: Int,
        row: Int,
        col: Int,
        ignore: PhoneCardPlacement? = null
    ): Boolean {
        return cardsForPage(pageIndex).any { card ->
            if (ignore != null && card == ignore) return@any false
            card.col == 0 &&
                    row in card.row until (card.row + CARD_SPAN_Y) &&
                    col in 0 until CARD_SPAN_X
        }
    }

    fun isCellCoveredByWidget(
        pageIndex: Int,
        row: Int,
        col: Int,
        ignoreWidgetId: Int? = null
    ): Boolean {
        return widgetsForPage(pageIndex).any { widget ->
            if (ignoreWidgetId != null && widget.appWidgetId == ignoreWidgetId) return@any false
            row in widget.cellY until (widget.cellY + widget.spanY) &&
                    col in widget.cellX until (widget.cellX + widget.spanX)
        }
    }

    fun computeDropPreview(pointerPx: Offset): DropPreview? {
        val payload = dragging ?: return null
        val targetCell = cellFromPointer(pointerPx) ?: return null
        val targetX = targetCell.first
        val targetY = targetCell.second

        val pageIndex = currentPage
        val pageSlots = slotsForPage(pageIndex)
        val pageCards = cardsForPage(pageIndex)
        val pageWidgets = widgetsForPage(pageIndex)

        return when (payload) {
            is DragPayload.App -> {
                val idx = targetY * COLS + targetX
                val slotPkg = pageSlots.getOrNull(idx)
                val occupiedByOtherApp = slotPkg != null && slotPkg != payload.pkg
                val coveredByCard = isCellCoveredByCard(pageIndex, targetY, targetX)
                val coveredByWidget = isCellCoveredByWidget(pageIndex, targetY, targetX)

                DropPreview(
                    cellX = targetX,
                    cellY = targetY,
                    spanX = 1,
                    spanY = 1,
                    isValid = !occupiedByOtherApp && !coveredByCard && !coveredByWidget
                )
            }

            is DragPayload.Widget -> {
                val valid = isAreaFreeForWidget(
                    slots = pageSlots,
                    cards = pageCards,
                    widgets = pageWidgets,
                    cellX = targetX,
                    cellY = targetY,
                    spanX = payload.spanX,
                    spanY = payload.spanY,
                    rowsToShow = rowsToShowState,
                    ignoreWidgetId = payload.widgetId
                )

                DropPreview(
                    cellX = targetX,
                    cellY = targetY,
                    spanX = payload.spanX,
                    spanY = payload.spanY,
                    isValid = valid
                )
            }

            is DragPayload.Card -> {
                val valid = isAreaFreeForCard(
                    slots = pageSlots,
                    cards = pageCards,
                    widgets = pageWidgets,
                    targetRow = targetY,
                    rowsToShow = rowsToShowState,
                    ignore = payload.placement
                )

                DropPreview(
                    cellX = 0,
                    cellY = targetY,
                    spanX = CARD_SPAN_X,
                    spanY = CARD_SPAN_Y,
                    isValid = valid
                )
            }
        }
    }

    fun maybeAutoTurnPage(pointerPx: Offset) {
        val now = SystemClock.uptimeMillis()
        if (now - lastEdgePageTurnAt < PHONE_HOME_EDGE_SWITCH_COOLDOWN_MS) return
        if (routeRootSizePx.width <= 0) return

        val x = pointerPx.x
        val width = routeRootSizePx.width.toFloat()

        when {
            x <= PHONE_HOME_EDGE_SWITCH_THRESHOLD_PX && currentPage > 0 -> {
                lastEdgePageTurnAt = now
                setPage(currentPage - 1)
                dropPreview = null
            }

            x >= width - PHONE_HOME_EDGE_SWITCH_THRESHOLD_PX && currentPage < PHONE_HOME_PAGE_COUNT - 1 -> {
                lastEdgePageTurnAt = now
                setPage(currentPage + 1)
                dropPreview = null
            }
        }
    }

    fun moveAppToIndex(pageIndex: Int, pkg: String, toIndex: Int, visibleSlots: Int) {
        if (toIndex !in 0 until visibleSlots) return

        ensurePageStorage()

        val targetPageIndex = pageIndex.coerceIn(0, PHONE_HOME_PAGE_COUNT - 1)
        val currentTargetPage = slotsForPage(targetPageIndex)
        val existing = currentTargetPage.getOrNull(toIndex)
        if (existing != null && existing != pkg) return

        val rebuiltPages = slotPages.mapIndexed { idx, page ->
            val rebuilt = page.toMutableList()

            for (i in rebuilt.indices) {
                if (rebuilt[i] == pkg) {
                    rebuilt[i] = null
                }
            }

            if (idx == targetPageIndex) {
                rebuilt[toIndex] = pkg
            }

            normalizeSlots(rebuilt)
        }

        slotPages = rebuiltPages
        persistSlots()
    }

    suspend fun deleteWidget(widgetId: Int) {
        widgetsRepo.remove(widgetId)
        runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
    }

    suspend fun upsertWidget(p: WidgetPlacement) {
        widgetsRepo.upsert(p)
    }

    fun finishDragAt(pointerPx: Offset) {
        hasDragPointer = true

        val payload = dragging
        val preview = computeDropPreview(pointerPx)

        if (payload == null || preview == null || !preview.isValid) {
            placeError = when (payload) {
                is DragPayload.App -> context.getString(R.string.phone_home_error_cannot_place_app_here)
                is DragPayload.Widget -> context.getString(R.string.phone_home_error_cannot_place_widget_here)
                is DragPayload.Card -> context.getString(R.string.phone_home_error_cannot_place_card_here)
                null -> placeError
            }
            stopDrag()
            dragPointerId = null
            suppressBackgroundLongPress = false
            return
        }

        when (payload) {
            is DragPayload.App -> {
                val targetIdx = preview.cellY * COLS + preview.cellX
                val visibleSlots = rowsToShowState * COLS
                moveAppToIndex(currentPage, payload.pkg, targetIdx, visibleSlots)
                clearPlaceError()
            }

            is DragPayload.Widget -> {
                scope.launch {
                    val providerString =
                        widgets.firstOrNull { it.appWidgetId == payload.widgetId }?.provider
                            ?: widgetsRepo.widgetsFlow
                                .firstOrNull()
                                ?.firstOrNull { it.appWidgetId == payload.widgetId }
                                ?.provider
                            ?: ""

                    upsertWidget(
                        WidgetPlacement(
                            appWidgetId = payload.widgetId,
                            provider = providerString,
                            cellX = preview.cellX,
                            cellY = preview.cellY,
                            spanX = payload.spanX,
                            spanY = payload.spanY,
                            layoutMode = if (isPortrait) {
                                WidgetLayoutMode.PORTRAIT
                            } else {
                                WidgetLayoutMode.LANDSCAPE
                            },
                            pageIndex = currentPage
                        )
                    )
                }
                clearPlaceError()
            }

            is DragPayload.Card -> {
                val idx = cards.indexOfFirst { it == payload.placement }
                if (idx != -1) {
                    cards[idx] = payload.placement.copy(
                        row = preview.cellY,
                        col = 0,
                        pageIndex = currentPage
                    )
                    persistCards()
                    clearPlaceError()
                } else {
                    placeError = context.getString(R.string.phone_home_error_card_not_found)
                }
            }
        }

        stopDrag()
        dragPointerId = null
        suppressBackgroundLongPress = false
    }

    val wallpaperBitmap by produceState<ImageBitmap?>(initialValue = null, wallpaperUri, hasHomeWallpaper) {
        val current = wallpaperUri
        value = if (!hasHomeWallpaper || current.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                loadPhoneWallpaperBitmap(context, Uri.parse(current))
            }
        }
    }

    ProvidePhoneWallpaperGlassState(
        state = PhoneWallpaperGlassState(
            imageBitmap = wallpaperBitmap,
            hasWallpaper = hasHomeWallpaper && wallpaperBitmap != null,
            rootSizePx = routeRootSizePx,
            userScale = wallpaperScale,
            offsetX = wallpaperOffsetX,
            offsetY = wallpaperOffsetY
        )
    ) {
        Box(
            modifier = Modifier
                .onSizeChanged { routeRootSizePx = it }
                .fillMaxSize()
                .background(baseBg)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { },
                    onLongClick = {
                        if (suppressBackgroundLongPress) return@combinedClickable
                        if (!drawerOpen && dragging == null && !addStuffOpen) {
                            homeQuickMenuOpen = true
                        }
                    }
                )
        ) {
            if (hasHomeWallpaper && wallpaperBitmap != null) {
                PhoneHomeWallpaperLayer(
                    imageBitmap = wallpaperBitmap!!,
                    userScale = wallpaperScale,
                    offsetX = wallpaperOffsetX,
                    offsetY = wallpaperOffsetY,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (!hasHomeWallpaper) {
                Box(Modifier.fillMaxSize().background(ambientOverlay))
            }

            if (dragging != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10_000f)
                        .pointerInput(dragging) {
                            awaitPointerEventScope {
                                while (dragging != null) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)

                                    val moveChange = dragPointerId?.let { id ->
                                        event.changes.firstOrNull { it.id == id }
                                    } ?: event.changes.firstOrNull()

                                    if (moveChange != null) {
                                        hasDragPointer = true
                                        maybeAutoTurnPage(moveChange.position)
                                        dropPreview = computeDropPreview(moveChange.position)
                                    }

                                    val upChange = dragPointerId?.let { id ->
                                        event.changes.firstOrNull { it.id == id && it.changedToUp() }
                                    } ?: event.changes.firstOrNull { it.changedToUp() }

                                    if (upChange != null) {
                                        finishDragAt(upChange.position)
                                        break
                                    }
                                }
                            }
                        }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val hintAreaHeight = 52.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hintAreaHeight)
                ) {
                    if (editMode) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.phone_home_edit_mode),
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    val err = placeError
                                    if (!err.isNullOrBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = err,
                                            color = Color(0xFFFF6B6B),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                PhoneHomePageDots(
                                    pageCount = PHONE_HOME_PAGE_COUNT,
                                    currentPage = currentPage
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Text(
                                    text = stringResource(R.string.common_done),
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { exitEditMode() },
                                            onLongClick = { exitEditMode() }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            PhoneHomePageDots(
                                pageCount = PHONE_HOME_PAGE_COUNT,
                                currentPage = currentPage
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

                    val density = LocalDensity.current
                    val pageWidthDp = with(density) { constraints.maxWidth.toDp() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(dragging, drawerOpen, addStuffOpen, currentPage) {
                                if (!isPortrait) return@pointerInput

                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        scope.launch {
                                            pageDragOffsetPx.stop()
                                            pageDragOffsetPx.snapTo(0f)
                                        }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        if (dragging != null) return@detectHorizontalDragGestures
                                        if (drawerOpen || addStuffOpen) return@detectHorizontalDragGestures

                                        val raw = pageDragOffsetPx.value + dragAmount

                                        val clamped = when {
                                            currentPage == 0 -> raw.coerceAtMost(0f)
                                            currentPage == PHONE_HOME_PAGE_COUNT - 1 -> raw.coerceAtLeast(0f)
                                            else -> raw
                                        }

                                        change.consume()
                                        scope.launch {
                                            pageDragOffsetPx.snapTo(clamped)
                                        }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            val targetPage = when {
                                                pageDragOffsetPx.value > PHONE_HOME_SWIPE_SWITCH_THRESHOLD_PX && currentPage > 0 -> currentPage - 1
                                                pageDragOffsetPx.value < -PHONE_HOME_SWIPE_SWITCH_THRESHOLD_PX && currentPage < PHONE_HOME_PAGE_COUNT - 1 -> currentPage + 1
                                                else -> currentPage
                                            }

                                            val direction = targetPage - currentPage
                                            val targetOffset = if (direction == 0) {
                                                0f
                                            } else {
                                                -direction * pageWidthPx
                                            }

                                            pageDragOffsetPx.animateTo(
                                                targetValue = targetOffset,
                                                animationSpec = tween(durationMillis = 220)
                                            )

                                            setPage(targetPage)
                                            pageDragOffsetPx.snapTo(0f)
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            pageDragOffsetPx.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(durationMillis = 220)
                                            )
                                        }
                                    }
                                )
                            }
                            .clip(RoundedCornerShape(28.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .width(pageWidthDp * PHONE_HOME_PAGE_COUNT)
                                .fillMaxHeight()
                                .offset {
                                    IntOffset(
                                        x = (-currentPage * pageWidthPx + pageDragOffsetPx.value).roundToInt(),
                                        y = 0
                                    )
                                }
                        ) {
                            repeat(PHONE_HOME_PAGE_COUNT) { pageIndex ->
                                val pageSlots = slotsForPage(pageIndex).toList()
                                val pageCards = cardsForPage(pageIndex)
                                val pageWidgets = widgetsForPage(pageIndex)

                                key(
                                    pageIndex,
                                    pageSlots,
                                    pageCards,
                                    pageWidgets
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = pageWidthDp * pageIndex)
                                            .width(pageWidthDp)
                                            .fillMaxHeight()
                                    ) {
                                        PhoneHomeGrid(
                                            modifier = Modifier.fillMaxSize(),
                                            slots = pageSlots,
                                            cards = pageCards,
                                            widgets = pageWidgets,
                                            appWidgetHost = appWidgetHost,
                                            appWidgetManager = appWidgetManager,
                                            editMode = editMode,
                                            dropPreview = if (pageIndex == currentPage) dropPreview else null,
                                            resolveLabelForSlot = { id -> resolveLabelForSlot(id) },
                                            resolveIconForSlot = { id -> resolveIconForSlot(id) },
                                            onRemoveFromHome = { id ->
                                                removeFromHome(id)
                                            },
                                            onLaunch = { pkg -> launch(pkg) },
                                            onDeleteWidget = { widgetId ->
                                                scope.launch { deleteWidget(widgetId) }
                                            },
                                            dragging = dragging,
                                            setDragging = { payload ->
                                                if (
                                                    payload is DragPayload.App ||
                                                    payload is DragPayload.Widget ||
                                                    payload is DragPayload.Card
                                                ) {
                                                    homeQuickMenuOpen = false
                                                    addStuffOpen = false
                                                    drawerOpen = false
                                                    clearPlaceError()
                                                    editMode = true
                                                    suppressBackgroundLongPress = true
                                                }
                                                dragging = payload
                                                dropPreview = null
                                            },
                                            hasDragPointer = hasDragPointer,
                                            setHasDragPointer = { value -> hasDragPointer = value },
                                            setDragPointer = { },
                                            updateDropPreview = { pointerPx ->
                                                dropPreview = computeDropPreview(pointerPx)
                                            },
                                            finishDragAt = { pointerPx ->
                                                finishDragAt(pointerPx)
                                            },
                                            setGridTopLeftPx = { value ->
                                                if (pageIndex == currentPage) gridTopLeftPx = value
                                            },
                                            setCellSizePx = { value ->
                                                if (pageIndex == currentPage) cellSizePx = value
                                            },
                                            setGapHPx = { value ->
                                                if (pageIndex == currentPage) gapHPx = value
                                            },
                                            setGapVPx = { value ->
                                                if (pageIndex == currentPage) gapVPx = value
                                            },
                                            setRowsToShowState = { value ->
                                                if (pageIndex == currentPage) rowsToShowState = value
                                            },
                                            setGridUsedWidth = { value ->
                                                if (pageIndex == currentPage) gridUsedWidth = value
                                            },
                                            setPhoneCellDp = { },
                                            clearPlaceError = { clearPlaceError() },
                                            onDeleteCard = { card ->
                                                cards.remove(card)
                                                persistCards()
                                            },
                                            addStuffOpen = addStuffOpen && pageIndex == currentPage,
                                            addStuffContent = { rowsToShow, cellDp ->
                                                if (pageIndex != currentPage) return@PhoneHomeGrid

                                                AddStuffPopup(
                                                    tab = addStuffTab,
                                                    onTabChange = { tabIndex ->
                                                        addStuffTab = tabIndex
                                                        addError = null
                                                    },
                                                    selectedCard = selectedCard,
                                                    onSelectCard = { cardType ->
                                                        selectedCard = cardType
                                                        addError = null
                                                    },
                                                    errorText = addError,
                                                    onCancel = {
                                                        addStuffOpen = false
                                                        selectedCard = null
                                                        addError = null
                                                    },
                                                    onConfirmAddCard = {
                                                        val selectedType = selectedCard
                                                        if (selectedType == null) {
                                                            addError = context.getString(R.string.phone_home_add_error_select_card)
                                                            return@AddStuffPopup
                                                        }

                                                        val pageCardList = cardsForPage(currentPage).toMutableList()

                                                        val ok = tryPlaceCard(
                                                            slots = slotsForPage(currentPage),
                                                            cards = pageCardList,
                                                            type = selectedType,
                                                            rowsToShow = rowsToShow,
                                                            pageIndex = currentPage
                                                        )

                                                        if (ok) {
                                                            cards.removeAll { it.pageIndex == currentPage }
                                                            cards.addAll(pageCardList)
                                                        }

                                                        if (!ok) {
                                                            addError = context.getString(R.string.phone_home_add_error_not_enough_card_space)
                                                            return@AddStuffPopup
                                                        }

                                                        persistCards()
                                                        addStuffOpen = false
                                                        selectedCard = null
                                                        addError = null
                                                    },
                                                    pm = pm,
                                                    cellDp = cellDp,
                                                    onPickWidget = { providerInfo, rawSpanX, rawSpanY ->
                                                        scope.launch {
                                                            val maxX = if (isPortrait) 4 else 2
                                                            val maxY = if (isPortrait) 5 else 2
                                                            val spanX = rawSpanX.coerceIn(1, maxX)
                                                            val spanY = rawSpanY.coerceIn(1, maxY)

                                                            val widgetId = appWidgetHost.allocateAppWidgetId()
                                                            val provider = providerInfo.provider ?: run {
                                                                placeError = context.getString(R.string.phone_home_widget_provider_error)
                                                                return@launch
                                                            }

                                                            val bound = runCatching {
                                                                appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)
                                                            }.getOrDefault(false)

                                                            if (!bound) {
                                                                runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
                                                                placeError = context.getString(R.string.phone_home_widget_bind_error)
                                                                return@launch
                                                            }

                                                            val best = nearestFreeWidgetCell(
                                                                slots = slotsForPage(currentPage),
                                                                cards = cardsForPage(currentPage),
                                                                widgets = widgetsForPage(currentPage),
                                                                startCellX = 0,
                                                                startCellY = 0,
                                                                spanX = spanX,
                                                                spanY = spanY,
                                                                rowsToShow = rowsToShowState,
                                                                ignoreWidgetId = null
                                                            )

                                                            if (best == null) {
                                                                runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
                                                                placeError = context.getString(R.string.phone_home_widget_no_space)
                                                                return@launch
                                                            }

                                                            upsertWidget(
                                                                WidgetPlacement(
                                                                    appWidgetId = widgetId,
                                                                    provider = provider.flattenToString(),
                                                                    cellX = best.first,
                                                                    cellY = best.second,
                                                                    spanX = spanX,
                                                                    spanY = spanY,
                                                                    layoutMode = if (isPortrait) {
                                                                        WidgetLayoutMode.PORTRAIT
                                                                    } else {
                                                                        WidgetLayoutMode.LANDSCAPE
                                                                    },
                                                                    pageIndex = currentPage
                                                                )
                                                            )

                                                            addStuffOpen = false
                                                            selectedCard = null
                                                            addError = null
                                                            clearPlaceError()
                                                            editMode = true
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                PortraitBlurDrawerButtonCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .then(
                            if (gridUsedWidth.value > 0f) Modifier.width(gridUsedWidth)
                            else Modifier.fillMaxWidth()
                        )
                        .height(58.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -20f) drawerOpen = true
                            }
                        }
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { drawerOpen = true },
                            onLongClick = { drawerOpen = true }
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.phone_drawer_title),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (homeQuickMenuOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(9000f)
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.58f))
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { homeQuickMenuOpen = false },
                                onLongClick = { homeQuickMenuOpen = false }
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(9001f)
                ) {
                    BubbleMenu(
                        title = stringResource(R.string.phone_home_quick_menu_title),
                        onDismiss = { homeQuickMenuOpen = false },
                        items = listOf(
                            stringResource(R.string.phone_home_quick_menu_add_cards) to {
                                homeQuickMenuOpen = false
                                addError = null
                                selectedCard = null
                                addStuffTab = 0
                                addStuffOpen = true
                            },
                            stringResource(R.string.phone_home_edit_mode) to {
                                homeQuickMenuOpen = false
                                if (!drawerOpen && !addStuffOpen) {
                                    editMode = true
                                }
                            },
                            stringResource(R.string.phone_home_menu_change_wallpaper) to {
                                homeQuickMenuOpen = false
                                addStuffOpen = false
                                drawerOpen = false
                                clearPlaceError()
                                showWallpaperPicker = true
                            }
                        )
                    )
                }
            }

            if (showWallpaperPicker) {
                WallpaperPickerScreen(
                    initialImageUri = wallpaperUri,
                    initialUserScale = wallpaperScale,
                    initialOffsetX = wallpaperOffsetX,
                    initialOffsetY = wallpaperOffsetY,
                    initialApplyHome = wallpaperApplyHome,
                    initialApplyLock = false,
                    onCancel = {
                        showWallpaperPicker = false
                    },
                    onClear = {
                        scope.launch {
                            settingsRepo.clearPhoneHomeWallpaper()
                        }
                    },
                    onApply = { result ->
                        scope.launch {
                            settingsRepo.setPhoneHomeWallpaper(
                                uri = result.imageUri,
                                scale = result.userScale,
                                offsetX = result.offsetX,
                                offsetY = result.offsetY,
                                applyHome = result.applyHome,
                                applyLock = result.applyLock
                            )
                            showWallpaperPicker = false
                        }
                    }
                )
            }

            PhoneHomeDrawer(
                open = drawerOpen,
                dragActive = drawerDragActive,
                onOpenChange = { open -> drawerOpen = open },
                onDragActiveChange = { active -> drawerDragActive = active },
                allApps = allApps,
                setDragging = { payload -> dragging = payload },
                setDragPointer = { _ ->
                    hasDragPointer = true
                },
                setHasDragPointer = { value -> hasDragPointer = value },
                updateDropPreview = { pointer ->
                    dropPreview = computeDropPreview(pointer)
                },
                onBeginEditDrag = {
                    dragPointerId = null
                    hasDragPointer = false
                    homeQuickMenuOpen = false
                    addStuffOpen = false
                    clearPlaceError()
                    editMode = true
                    suppressBackgroundLongPress = true
                },
                onEndEditDrag = {
                    suppressBackgroundLongPress = false
                },
                onLaunch = { pkg -> launch(pkg) },
                clearPlaceError = { clearPlaceError() },
                finishDragAt = { pointer -> finishDragAt(pointer) }
            )

            val rootPreview = dropPreview
            if (
                drawerDragActive &&
                dragging is DragPayload.App &&
                rootPreview != null &&
                hasDragPointer
            ) {
                val density = LocalDensity.current

                val px = (
                        gridTopLeftPx.x +
                                rootPreview.cellX * (cellSizePx + gapHPx)
                        ).roundToInt()

                val py = (
                        gridTopLeftPx.y +
                                rootPreview.cellY * (cellSizePx + gapVPx)
                        ).roundToInt()

                val pw = (
                        rootPreview.spanX * cellSizePx +
                                (rootPreview.spanX - 1) * gapHPx
                        ).roundToInt()

                val ph = (
                        rootPreview.spanY * cellSizePx +
                                (rootPreview.spanY - 1) * gapVPx
                        ).roundToInt()

                val ghostBorder = if (rootPreview.isValid) {
                    Color.LightGray.copy(alpha = 0.95f)
                } else {
                    Color(0xFFFF5A5A).copy(alpha = 0.98f)
                }

                val ghostFill = if (rootPreview.isValid) {
                    Color.LightGray.copy(alpha = 0.18f)
                } else {
                    Color(0xFFFF5A5A).copy(alpha = 0.18f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(20_000f)
                ) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(px, py) }
                            .size(
                                width = with(density) { pw.toDp() },
                                height = with(density) { ph.toDp() }
                            )
                            .border(
                                width = 2.dp,
                                color = ghostBorder,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .padding(2.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ghostFill),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun PhoneHomeWallpaperLayer(
    imageBitmap: ImageBitmap,
    userScale: Float,
    offsetX: Float,
    offsetY: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val frameWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val frameHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val bitmapWidth = imageBitmap.width.toFloat().coerceAtLeast(1f)
        val bitmapHeight = imageBitmap.height.toFloat().coerceAtLeast(1f)

        val baseFillScale = max(
            frameWidth / bitmapWidth,
            frameHeight / bitmapHeight
        )

        val currentUserScale = userScale.coerceIn(1f, 6f)
        val effectiveScale = baseFillScale * currentUserScale

        val drawnWidth = bitmapWidth * effectiveScale
        val drawnHeight = bitmapHeight * effectiveScale

        val maxOffsetX = ((drawnWidth - frameWidth) / 2f).coerceAtLeast(0f)
        val maxOffsetY = ((drawnHeight - frameHeight) / 2f).coerceAtLeast(0f)

        val normalizedOffsetX = offsetX.coerceIn(-1f, 1f)
        val normalizedOffsetY = offsetY.coerceIn(-1f, 1f)

        val appliedOffsetX = normalizedOffsetX * maxOffsetX
        val appliedOffsetY = normalizedOffsetY * maxOffsetY

        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = currentUserScale
                    scaleY = currentUserScale
                    translationX = appliedOffsetX
                    translationY = appliedOffsetY

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = AndroidRenderEffect.createBlurEffect(
                            0f,
                            0f,
                            Shader.TileMode.DECAL
                        ).asComposeRenderEffect()
                    }
                }
        )
    }
}

private fun loadPhoneWallpaperBitmap(
    context: android.content.Context,
    uri: Uri
): ImageBitmap? {
    return runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val srcW = info.size.width.coerceAtLeast(1)
                val srcH = info.size.height.coerceAtLeast(1)
                val longest = max(srcW, srcH)
                if (longest > 4096) {
                    val factor = longest / 4096f
                    decoder.setTargetSize(
                        (srcW / factor).toInt().coerceAtLeast(1),
                        (srcH / factor).toInt().coerceAtLeast(1)
                    )
                }
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bitmap ->
                    scalePhoneWallpaperBitmapDownIfNeeded(bitmap)
                }
            }
        } ?: return null

        bitmap.asImageBitmap()
    }.getOrNull()
}

private fun scalePhoneWallpaperBitmapDownIfNeeded(bitmap: Bitmap): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= 4096) return bitmap

    val factor = longest / 4096f
    val targetW = (bitmap.width / factor).toInt().coerceAtLeast(1)
    val targetH = (bitmap.height / factor).toInt().coerceAtLeast(1)

    return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
}

@Composable
private fun PortraitBlurDrawerButtonCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    WallpaperGlassSurface(
        modifier = modifier,
        shape = shape,
        baseContainerColor = Color.White.copy(alpha = 0.10f),
        enableBlur = isPortrait,
        blurRadiusPx = if (isPortrait) {
            PhoneGlass.PORTRAIT_BLUR_RADIUS
        } else {
            PhoneGlass.LANDSCAPE_BLUR_RADIUS
        },
        dimAlpha = if (isPortrait) {
            PhoneGlass.PORTRAIT_DIM_ALPHA
        } else {
            0f
        },
        overlayAlpha = if (isPortrait) {
            PhoneGlass.PORTRAIT_OVERLAY_ALPHA
        } else {
            PhoneGlass.LANDSCAPE_OVERLAY_ALPHA
        },
        content = content
    )
}