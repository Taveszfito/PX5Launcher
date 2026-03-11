@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import com.dueboysenberry1226.px5launcher.data.WidgetLayoutMode
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dueboysenberry1226.px5launcher.data.LauncherRepository
import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import com.dueboysenberry1226.px5launcher.data.WidgetsRepository
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val WIDGET_HOST_ID = 1024

@Composable
fun PhoneHomeRoute(
    pm: PackageManager,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT

    val scopeLocale = remember { Locale.getDefault() }
    val scope = rememberCoroutineScope()

    val repo = remember(pm, context) { LauncherRepository(context, pm) }
    val recents by repo.recentsFlow.collectAsState(initial = emptyList())

    // ✅ Widgets repo + flow
    val widgetsRepo = remember(context) { WidgetsRepository(context) }

    val currentWidgetLayoutMode = remember(isPortrait) {
        if (isPortrait) WidgetLayoutMode.PORTRAIT else WidgetLayoutMode.LANDSCAPE
    }

    val widgets by widgetsRepo
        .widgetsFlow(currentWidgetLayoutMode)
        .collectAsState(initial = emptyList())

    // ✅ AppWidget host/manager
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val appWidgetHost = remember(context) { AppWidgetHost(context, WIDGET_HOST_ID) }

    DisposableEffect(Unit) {
        appWidgetHost.startListening()
        onDispose { appWidgetHost.stopListening() }
    }

    // ===== Ambient light =====
    val ambientColor = remember { mutableStateOf(Color(0xFF101826)) }

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

    // --- Apps load ---
    var allApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.Default) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0)
                .map { ri ->
                    AppItem(
                        label = ri.loadLabel(pm)?.toString().orEmpty(),
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

    // Ambient a legutóbb megnyitott app alapján (recents[0])
    LaunchedEffect(recents, allApps) {
        val pkg = recents.firstOrNull() ?: return@LaunchedEffect
        val app = allApps.firstOrNull { it.packageName == pkg }
        setAmbientFromApp(ambientColor, app)
    }

    // ===== Persisted: slots + cards =====
    val storedSlots by repo.phoneHomeSlotsFlow.collectAsState(initial = emptyList())
    val storedCards by repo.phoneHomeCardsFlow.collectAsState(initial = emptyList())

    val slots = remember { mutableStateListOf<String?>() }
    val cards = remember { mutableStateListOf<PhoneCardPlacement>() }

    fun persistSlots() {
        val snap = slots.take(SLOTS).toList()
        scope.launch { repo.savePhoneHomeSlots(snap) }
    }

    fun persistCards() {
        val snap = cards.toList()
        scope.launch { repo.savePhoneHomeCards(snap) }
    }

    LaunchedEffect(storedSlots) {
        val norm = normalizeSlots(storedSlots)
        slots.clear()
        slots.addAll(norm)
    }

    LaunchedEffect(storedCards) {
        cards.clear()
        cards.addAll(storedCards)
    }

    // uninstall/tiltás pucolás
    LaunchedEffect(allApps) {
        if (allApps.isEmpty() || slots.isEmpty()) return@LaunchedEffect
        val valid = allApps.asSequence().map { it.packageName }.toHashSet()
        var changed = false
        for (i in 0 until slots.size) {
            val id = slots[i]
            if (id != null && id !in valid) {
                slots[i] = null
                changed = true
            }
        }
        if (changed) persistSlots()
    }

    fun resolveLabelForSlot(id: String?): String {
        if (id == null) return ""
        return allApps.firstOrNull { it.packageName == id }?.label ?: id
    }

    fun resolveIconForSlot(id: String?) =
        if (id == null) null else allApps.firstOrNull { it.packageName == id }?.icon

    fun removeFromHome(id: String) {
        val idx = slots.indexOfFirst { it == id }
        if (idx != -1) {
            slots[idx] = null
            persistSlots()
        }
    }

    // ===== UI states =====
    var placeError by remember { mutableStateOf<String?>(null) }
    fun clearPlaceError() { placeError = null }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var drawerDragActive by remember { mutableStateOf(false) }

    var homeQuickMenuOpen by remember { mutableStateOf(false) }
    var addStuffOpen by remember { mutableStateOf(false) }
    var addStuffTab by remember { mutableStateOf(0) }
    var selectedCard by remember { mutableStateOf<PhoneCardType?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    var suppressBackgroundLongPress by remember { mutableStateOf(false) }

    var editMode by rememberSaveable { mutableStateOf(false) }
    fun exitEditMode() {
        editMode = false
        clearPlaceError()
    }

    // ===== Drag state =====
    var dragging by remember { mutableStateOf<DragPayload?>(null) }
    var dragPointerPx by remember { mutableStateOf(Offset.Zero) }
    var hasDragPointer by remember { mutableStateOf(false) }
    var dragPointerId by remember { mutableStateOf<PointerId?>(null) }
    var dropPreview by remember { mutableStateOf<DropPreview?>(null) }

    // Grid geometry (root coords)
    var gridTopLeftPx by remember { mutableStateOf(Offset.Zero) }
    var cellSizePx by remember { mutableStateOf(0f) }
    var gapHPx by remember { mutableStateOf(0f) }
    var gapVPx by remember { mutableStateOf(0f) }
    var rowsToShowState by remember { mutableStateOf(1) }

    fun stopDrag() {
        dragging = null
        hasDragPointer = false
        dropPreview = null
    }


    fun slotIndexFromPointer(pointerPx: Offset): Int? {
        val localX = pointerPx.x - gridTopLeftPx.x
        val localY = pointerPx.y - gridTopLeftPx.y
        if (localX < 0f || localY < 0f) return null

        val stepX = cellSizePx + gapHPx
        val stepY = cellSizePx + gapVPx

        val col = (localX / stepX).toInt()
        val row = (localY / stepY).toInt()

        if (col !in 0 until COLS) return null
        if (row !in 0 until rowsToShowState) return null

        return row * COLS + col
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
        row: Int,
        col: Int,
        ignore: PhoneCardPlacement? = null
    ): Boolean {
        return cards.any { card ->
            if (ignore != null && card == ignore) return@any false
            card.col == 0 &&
                    row in card.row until (card.row + CARD_SPAN_Y) &&
                    col in card.col until (card.col + CARD_SPAN_X)
        }
    }

    fun isCellCoveredByWidget(
        row: Int,
        col: Int,
        ignoreWidgetId: Int? = null
    ): Boolean {
        return widgets.any { w ->
            if (ignoreWidgetId != null && w.appWidgetId == ignoreWidgetId) return@any false
            row in w.cellY until (w.cellY + w.spanY) &&
                    col in w.cellX until (w.cellX + w.spanX)
        }
    }

    fun computeDropPreview(pointerPx: Offset): DropPreview? {
        val payload = dragging ?: return null
        val targetCell = cellFromPointer(pointerPx) ?: return null
        val (targetX, targetY) = targetCell

        return when (payload) {
            is DragPayload.App -> {
                val idx = targetY * COLS + targetX
                val slotPkg = slots.getOrNull(idx)
                val occupiedByOtherApp = slotPkg != null && slotPkg != payload.pkg
                val coveredByCard = isCellCoveredByCard(targetY, targetX)
                val coveredByWidget = isCellCoveredByWidget(targetY, targetX)

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
                    slots = slots,
                    cards = cards,
                    widgets = widgets,
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
                    slots = slots,
                    cards = cards,
                    widgets = widgets,
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

    fun moveAppToIndex(pkg: String, toIndex: Int, visibleSlots: Int) {
        if (toIndex !in 0 until visibleSlots) return

        val existing = slots.getOrNull(toIndex)
        if (existing != null && existing != pkg) return

        val oldIdx = slots.indexOfFirst { it == pkg }
        if (oldIdx != -1) slots[oldIdx] = null

        slots[toIndex] = pkg
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
        dragPointerPx = pointerPx
        hasDragPointer = true

        val payload = dragging
        val preview = computeDropPreview(pointerPx)

        if (payload == null || preview == null || !preview.isValid) {
            placeError = when (payload) {
                is DragPayload.App -> "Ide nem lehet appot lerakni."
                is DragPayload.Widget -> "Ide nem lehet widgetet lerakni."
                is DragPayload.Card -> "Ide nem lehet kártyát lerakni."
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
                moveAppToIndex(payload.pkg, targetIdx, visibleSlots)
                clearPlaceError()
            }

            is DragPayload.Widget -> {
                scope.launch {
                    upsertWidget(
                        WidgetPlacement(
                            appWidgetId = payload.widgetId,
                            provider = widgets.firstOrNull { it.appWidgetId == payload.widgetId }?.provider ?: "",
                            cellX = preview.cellX,
                            cellY = preview.cellY,
                            spanX = payload.spanX,
                            spanY = payload.spanY,
                            layoutMode = if (isPortrait) {
                                WidgetLayoutMode.PORTRAIT
                            } else {
                                WidgetLayoutMode.LANDSCAPE
                            }
                        )
                    )
                }
                clearPlaceError()
            }

            is DragPayload.Card -> {
                val idx = cards.indexOfFirst { it == payload.placement }
                if (idx != -1) {
                    cards[idx] = payload.placement.copy(row = preview.cellY, col = 0)
                    persistCards()
                    clearPlaceError()
                } else {
                    placeError = "A kártya nem található."
                }
            }
        }

        stopDrag()
        dragPointerId = null
        suppressBackgroundLongPress = false
    }

    // grid used sizes
    var gridUsedWidth by remember { mutableStateOf(0.dp) }
    var phoneCellDp by remember { mutableStateOf(96.dp) }

    // ===== ROOT =====
    Box(
        modifier = Modifier
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
        Box(Modifier.fillMaxSize().background(ambientOverlay))

        // ✅ GLOBAL DRAG TRACKER + DROP (root coords)
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
                                    dragPointerPx = moveChange.position
                                    hasDragPointer = true
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
            // ===== Hint area =====
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
                                    text = "Szerkesztés mód",
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

                            Text(
                                text = "Kész",
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
                }
            }

            Spacer(Modifier.height(10.dp))

            PhoneHomeGrid(
                modifier = Modifier.weight(1f),

                slots = slots,
                cards = cards,
                widgets = widgets,

                appWidgetHost = appWidgetHost,
                appWidgetManager = appWidgetManager,

                editMode = editMode,
                dropPreview = dropPreview,
                resolveLabelForSlot = { resolveLabelForSlot(it) },
                resolveIconForSlot = { resolveIconForSlot(it) },
                onRemoveFromHome = { removeFromHome(it) },
                onLaunch = { launch(it) },

                onDeleteWidget = { widgetId ->
                    scope.launch {
                        deleteWidget(widgetId)
                    }
                },

                dragging = dragging,
                setDragging = { payload ->
                    if (payload is DragPayload.App || payload is DragPayload.Widget || payload is DragPayload.Card) {
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
                dragPointerPx = dragPointerPx,
                hasDragPointer = hasDragPointer,
                setHasDragPointer = { hasDragPointer = it },


                setDragPointer = { dragPointerPx = it },
                updateDropPreview = { pointerPx ->
                    dropPreview = computeDropPreview(pointerPx)
                },
                finishDragAt = { finishDragAt(it) },

                setGridTopLeftPx = { gridTopLeftPx = it },
                setCellSizePx = { cellSizePx = it },
                setGapHPx = { gapHPx = it },
                setGapVPx = { gapVPx = it },
                setRowsToShowState = { rowsToShowState = it },

                setGridUsedWidth = { gridUsedWidth = it },
                setPhoneCellDp = { phoneCellDp = it },

                moveAppToIndex = { pkg, idx, vis -> moveAppToIndex(pkg, idx, vis) },
                clearPlaceError = { clearPlaceError() },

                onMoveCard = { card, targetRow ->
                    val bestRow = nearestFreeCardRow(
                        slots = slots,
                        cards = cards,
                        widgets = widgets,
                        targetRow = targetRow,
                        rowsToShow = rowsToShowState,
                        ignore = card
                    )
                    if (bestRow != null && bestRow != card.row) {
                        cards.remove(card)
                        cards.add(card.copy(row = bestRow, col = 0))
                        persistCards()
                    }
                },
                onDeleteCard = { card ->
                    cards.remove(card)
                    persistCards()
                },

                addStuffOpen = addStuffOpen,
                addStuffContent = { rowsToShow, cellDp ->
                    AddStuffPopup(
                        tab = addStuffTab,
                        onTabChange = { addStuffTab = it; addError = null },
                        selectedCard = selectedCard,
                        onSelectCard = { selectedCard = it; addError = null },
                        errorText = addError,
                        onCancel = {
                            addStuffOpen = false
                            selectedCard = null
                            addError = null
                        },
                        onConfirmAddCard = { _ ->
                            val t = selectedCard
                            if (t == null) {
                                addError = "Válassz ki egy kártyát."
                                return@AddStuffPopup
                            }
                            val ok = tryPlaceCard(
                                slots = slots,
                                cards = cards,
                                type = t,
                                rowsToShow = rowsToShow
                            )
                            if (!ok) {
                                addError = "Nincs elég szabad hely (2×4) a képernyőn."
                                return@AddStuffPopup
                            }
                            persistCards()
                            addStuffOpen = false
                            selectedCard = null
                            addError = null
                        },
                        pm = pm,
                        cellDp = cellDp,

                        // ✅ Widget pick: allocate + bind + place
                        onPickWidget = { providerInfo, rawSpanX, rawSpanY ->
                            scope.launch {
                                val maxX = if (isPortrait) 4 else 2
                                val maxY = if (isPortrait) 5 else 2
                                val spanX = rawSpanX.coerceIn(1, maxX)
                                val spanY = rawSpanY.coerceIn(1, maxY)

                                val widgetId = appWidgetHost.allocateAppWidgetId()
                                val provider = providerInfo.provider ?: run {
                                    placeError = "Widget provider hiba."
                                    return@launch
                                }

                                val bound = runCatching {
                                    appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)
                                }.getOrDefault(false)

                                if (!bound) {
                                    runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
                                    placeError = "A widget nem köthető (engedély kellhet)."
                                    return@launch
                                }

                                val best = nearestFreeWidgetCell(
                                    slots = slots,
                                    cards = cards,
                                    widgets = widgets,
                                    startCellX = 0,
                                    startCellY = 0,
                                    spanX = spanX,
                                    spanY = spanY,
                                    rowsToShow = rowsToShowState,
                                    ignoreWidgetId = null
                                )

                                if (best == null) {
                                    runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
                                    placeError = "Nincs elég szabad hely a widgetnek."
                                    return@launch
                                }

                                val (bx, by) = best
                                upsertWidget(
                                    WidgetPlacement(
                                        appWidgetId = widgetId,
                                        provider = provider.flattenToString(),
                                        cellX = bx,
                                        cellY = by,
                                        spanX = spanX,
                                        spanY = spanY,
                                        layoutMode = if (isPortrait) {
                                            WidgetLayoutMode.PORTRAIT
                                        } else {
                                            WidgetLayoutMode.LANDSCAPE
                                        }
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

        // ===== FIX App lista gomb =====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "App lista",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ===== ÜRES HÁTTÉR QUICK MENU =====
        if (homeQuickMenuOpen) {
            BubbleMenu(
                title = "Menü",
                onDismiss = { homeQuickMenuOpen = false },
                items = listOf(
                    "Kártyák hozzáadása" to {
                        homeQuickMenuOpen = false
                        addError = null
                        selectedCard = null
                        addStuffTab = 0
                        addStuffOpen = true
                    },
                    "Szerkesztés mód" to {
                        homeQuickMenuOpen = false
                        if (!drawerOpen && !addStuffOpen) {
                            editMode = true
                        }
                    }
                )
            )
        }

        // ===== DRAWER =====
        PhoneHomeDrawer(
            open = drawerOpen,
            dragActive = drawerDragActive,
            onOpenChange = { drawerOpen = it },
            onDragActiveChange = { drawerDragActive = it },

            allApps = allApps,
            rowsToShowState = rowsToShowState,

            dragging = dragging,
            setDragging = { dragging = it },

            setDragPointer = { dragPointerPx = it },
            setHasDragPointer = { hasDragPointer = it },

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

            onLaunch = { launch(it) },

            slotIndexFromPointer = { slotIndexFromPointer(it) },
            nearestFreeSlot = { idx, vis -> nearestFreeSlot(slots, idx, vis) },
            moveAppToIndex = { pkg, idx, vis -> moveAppToIndex(pkg, idx, vis) },

            placeErrorSet = { placeError = it },
            clearPlaceError = { clearPlaceError() },
            finishDragAt = { finishDragAt(it) }
        )
    }
}

