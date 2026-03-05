@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dueboysenberry1226.px5launcher.data.LauncherRepository
import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PhoneHomeRoute(
    pm: PackageManager,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scopeLocale = remember { Locale.getDefault() }
    val scope = rememberCoroutineScope()

    val repo = remember(pm, context) { LauncherRepository(context, pm) }
    val recents by repo.recentsFlow.collectAsState(initial = emptyList())

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
    fun exitEditMode() { editMode = false; clearPlaceError() }

    // ===== Drag state =====
    var dragging by remember { mutableStateOf<DragPayload?>(null) }
    var dragPointerPx by remember { mutableStateOf(Offset.Zero) }
    var hasDragPointer by remember { mutableStateOf(false) }
    var dragPointerId by remember { mutableStateOf<PointerId?>(null) }

    // Grid geometry (root coords)
    var gridTopLeftPx by remember { mutableStateOf(Offset.Zero) }
    var cellSizePx by remember { mutableStateOf(0f) }
    var gapHPx by remember { mutableStateOf(0f) }
    var gapVPx by remember { mutableStateOf(0f) }
    var rowsToShowState by remember { mutableStateOf(1) }

    fun stopDrag() {
        dragging = null
        hasDragPointer = false
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

    fun moveAppToIndex(pkg: String, toIndex: Int, visibleSlots: Int) {
        if (toIndex !in 0 until visibleSlots) return

        val existing = slots.getOrNull(toIndex)
        if (existing != null && existing != pkg) return

        val oldIdx = slots.indexOfFirst { it == pkg }
        if (oldIdx != -1) slots[oldIdx] = null

        slots[toIndex] = pkg
        persistSlots()
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
                                }

                                val upChange = dragPointerId?.let { id ->
                                    event.changes.firstOrNull { it.id == id && it.changedToUp() }
                                } ?: event.changes.firstOrNull { it.changedToUp() }

                                if (upChange != null) {
                                    dragPointerPx = upChange.position
                                    hasDragPointer = true

                                    val payload = dragging
                                    if (payload is DragPayload.App) {
                                        val visibleSlots = rowsToShowState * COLS
                                        val targetIdx = slotIndexFromPointer(dragPointerPx)
                                        val best = if (targetIdx != null) {
                                            nearestFreeSlot(slots, targetIdx, visibleSlots)
                                        } else null

                                        if (best != null) {
                                            moveAppToIndex(payload.pkg, best, visibleSlots)
                                            clearPlaceError()
                                        } else {
                                            placeError = "Nincs szabad slot."
                                        }
                                    }

                                    stopDrag()
                                    dragPointerId = null
                                    suppressBackgroundLongPress = false
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

            // ✅ itt legyen a weight, mert EZ ColumnScope
            PhoneHomeGrid(
                modifier = Modifier.weight(1f),

                slots = slots,
                cards = cards,
                editMode = editMode,
                resolveLabelForSlot = { resolveLabelForSlot(it) },
                resolveIconForSlot = { resolveIconForSlot(it) },
                onRemoveFromHome = { removeFromHome(it) },
                onLaunch = { launch(it) },

                dragging = dragging,
                setDragging = { payload ->
                    if (payload is DragPayload.App) {
                        homeQuickMenuOpen = false
                        addStuffOpen = false
                        drawerOpen = false
                        clearPlaceError()
                        editMode = true
                        suppressBackgroundLongPress = true
                    }
                    dragging = payload
                },
                dragPointerPx = dragPointerPx,
                hasDragPointer = hasDragPointer,
                setHasDragPointer = { hasDragPointer = it },

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
                        onPickWidget = { _, _, _ ->
                            addStuffOpen = false
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
            clearPlaceError = { clearPlaceError() }
        )
    }
}