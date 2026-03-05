@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.graphics.graphicsLayer
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dueboysenberry1226.px5launcher.data.LauncherRepository
import com.dueboysenberry1226.px5launcher.data.PhoneCardPlacement
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import com.dueboysenberry1226.px5launcher.ui.CalendarPanelCard
import com.dueboysenberry1226.px5launcher.ui.MusicControlPanelCard
import com.dueboysenberry1226.px5launcher.ui.widgets.WidgetPickerScreen
import com.dueboysenberry1226.px5launcher.ui.widgets.rememberWidgetPickerState
import com.dueboysenberry1226.px5launcher.util.computeDominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

private const val COLS = 4
private const val MAX_ROWS = 7
private const val SLOTS = COLS * MAX_ROWS

private data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

private sealed class DrawerEntry {
    data class Header(val letter: String) : DrawerEntry()
    data class App(val app: AppItem, val letter: String) : DrawerEntry()
}

private sealed class DragPayload {
    data class App(val pkg: String, val fromIndex: Int) : DragPayload()
    data class Card(val placement: PhoneCardPlacement) : DragPayload()
}

@Composable
fun PhoneHomeRoute(
    pm: PackageManager,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scopeLocale = remember { Locale.getDefault() }
    val scope = rememberCoroutineScope()

    val repo = remember(pm, context) { LauncherRepository(context, pm) }
    val recents by repo.recentsFlow.collectAsState(initial = emptyList())

    // ===== Ambient light =====
    var ambientColor by remember { mutableStateOf(Color(0xFF101826)) }

    val baseBg = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0B1020),
                Color(0xFF070A12),
                Color(0xFF05070D)
            )
        )
    }
    val ambientOverlay = remember(ambientColor) {
        Brush.verticalGradient(
            listOf(
                ambientColor.copy(alpha = 0.28f),
                ambientColor.copy(alpha = 0.12f),
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

    suspend fun setAmbientFromApp(app: AppItem?) {
        if (app == null) return
        val bmp = drawableToBitmap(app.icon) ?: return
        val c: Color = computeDominantColor(bmp)
        ambientColor = if (c.alpha <= 0.01f) Color(0xFF101826) else c
    }

    // ✅ Ambient a legutóbb megnyitott app alapján (recents[0])
    LaunchedEffect(recents, allApps) {
        val pkg = recents.firstOrNull() ?: return@LaunchedEffect
        val app = allApps.firstOrNull { it.packageName == pkg }
        setAmbientFromApp(app)
    }

    // ==========================
    // ✅ PERSISTED: slots + cards
    // ==========================
    val storedSlots by repo.phoneHomeSlotsFlow.collectAsState(initial = emptyList())
    val storedCards by repo.phoneHomeCardsFlow.collectAsState(initial = emptyList())

    fun normalizeSlots(list: List<String?>): List<String?> {
        val out = ArrayList<String?>(SLOTS)
        for (i in 0 until SLOTS) out += list.getOrNull(i)?.trim().takeUnless { it.isNullOrBlank() }
        return out
    }

    val slots = remember { mutableStateListOf<String?>() }
    val cards = remember { mutableStateListOf<PhoneCardPlacement>() }

    // ====== Drop feedback ======
    var placeError by remember { mutableStateOf<String?>(null) }
    fun clearPlaceError() { placeError = null }

    // kártya fix méret
    val cardSpanX = 4
    val cardSpanY = 2

    fun isAreaFreeForCard(targetRow: Int, rowsToShow: Int, ignore: PhoneCardPlacement? = null): Boolean {
        if (targetRow < 0) return false
        if (targetRow + cardSpanY > rowsToShow) return false

        val visibleSlots = rowsToShow * COLS
        val occ = BooleanArray(visibleSlots) { false }

        // app slotok
        for (i in 0 until visibleSlots) {
            if (slots.getOrNull(i) != null) occ[i] = true
        }

        // kártyák (mind 2x4, col=0)
        cards.forEach { c ->
            if (ignore != null && c == ignore) return@forEach
            if (c.col != 0) return@forEach
            for (dy in 0 until cardSpanY) {
                for (dx in 0 until cardSpanX) {
                    val rr = c.row + dy
                    val cc = c.col + dx
                    if (rr in 0 until rowsToShow && cc in 0 until COLS) {
                        val idx = rr * COLS + cc
                        if (idx in 0 until visibleSlots) occ[idx] = true
                    }
                }
            }
        }

        // célterület (col=0)
        for (dy in 0 until cardSpanY) {
            for (dx in 0 until cardSpanX) {
                val idx = (targetRow + dy) * COLS + dx
                if (idx !in 0 until visibleSlots) return false
                if (occ[idx]) return false
            }
        }
        return true
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

    fun persistSlots() {
        val snap = slots.take(SLOTS).toList()
        scope.launch { repo.savePhoneHomeSlots(snap) }
    }

    fun persistCards() {
        val snap = cards.toList()
        scope.launch { repo.savePhoneHomeCards(snap) }
    }

    // ha uninstall/tiltás miatt eltűnt egy app, pucoljuk ki a slotból
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

    fun resolveIconForSlot(id: String?): Drawable? {
        if (id == null) return null
        return allApps.firstOrNull { it.packageName == id }?.icon
    }

    fun removeFromHome(id: String) {
        val idx = slots.indexOfFirst { it == id }
        if (idx != -1) {
            slots[idx] = null
            persistSlots()
        }
    }

    // --- Drawer popup ---
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var drawerDragActive by remember { mutableStateOf(false) } // drawer látszólag eltűnik, de gesture él
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    val filteredApps = remember(allApps, search.text) {
        val q = search.text.trim().lowercase(scopeLocale)
        if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase(scopeLocale).contains(q) }
    }

    fun letterOf(app: AppItem): String {
        val ch = app.label.trim().firstOrNull()?.uppercaseChar()
        return when {
            ch == null -> "#"
            ch.isLetterOrDigit() -> ch.toString()
            else -> "#"
        }
    }

    val drawerEntries = remember(filteredApps) {
        val out = mutableListOf<DrawerEntry>()
        var last: String? = null
        filteredApps.forEach { app ->
            val l = letterOf(app)
            if (l != last) {
                out += DrawerEntry.Header(l)
                last = l
            }
            out += DrawerEntry.App(app, l)
        }
        out
    }

    val drawerListState = rememberLazyListState()
    var activeLetter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(drawerEntries, drawerListState.firstVisibleItemIndex) {
        val start = drawerListState.firstVisibleItemIndex.coerceIn(0, (drawerEntries.size - 1).coerceAtLeast(0))
        val appEntry = drawerEntries.drop(start).firstOrNull { it is DrawerEntry.App } as? DrawerEntry.App
        activeLetter = appEntry?.letter
    }

    LaunchedEffect(search.text) {
        if (drawerEntries.isNotEmpty()) drawerListState.scrollToItem(0)
    }

    // --- grid méretek state: hogy az App lista gomb szélessége passzoljon ---
    var gridUsedWidth by remember { mutableStateOf(0.dp) }
    var phoneCellDp by remember { mutableStateOf(96.dp) } // ✅ AddStuffPopup-hoz kell

    // ==========================
    // ✅ Menü/popup state
    // ==========================
    var homeQuickMenuOpen by remember { mutableStateOf(false) }
    var addStuffOpen by remember { mutableStateOf(false) }
    var addStuffTab by remember { mutableStateOf(0) } // 0=Cards, 1=Widgets
    var selectedCard by remember { mutableStateOf<PhoneCardType?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    var suppressBackgroundLongPress by remember { mutableStateOf(false) }

    var editMode by rememberSaveable { mutableStateOf(false) }
    fun exitEditMode() { editMode = false; clearPlaceError() }

    // ==========================
    // ✅ Drag state (csak DnD, nincs régi "kattintós place" rendszer)
    // ==========================
    var dragging by remember { mutableStateOf<DragPayload?>(null) }
    var dragPointerPx by remember { mutableStateOf(Offset.Zero) }
    var hasDragPointer by remember { mutableStateOf(false) }
    var dragPointerId by remember { mutableStateOf<PointerId?>(null) } // ✅ melyik ujj húz?

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

    // index számítás a pointer pozícióból (root pointer -> grid local)
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

    fun nearestFreeSlot(startIdx: Int, visibleSlots: Int): Int? {
        if (startIdx !in 0 until visibleSlots) return null
        if (slots.getOrNull(startIdx) == null) return startIdx

        val startRow = startIdx / COLS
        val startCol = startIdx % COLS

        var best: Int? = null
        var bestDist = Int.MAX_VALUE

        for (i in 0 until visibleSlots) {
            if (slots.getOrNull(i) != null) continue
            val r = i / COLS
            val c = i % COLS
            val d = kotlin.math.abs(r - startRow) + kotlin.math.abs(c - startCol)
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }

    fun moveAppToIndex(pkg: String, toIndex: Int, visibleSlots: Int) {
        if (toIndex !in 0 until visibleSlots) return

        val existing = slots.getOrNull(toIndex)
        if (existing != null && existing != pkg) {
            // foglalt → nem írjuk felül
            return
        }

        val oldIdx = slots.indexOfFirst { it == pkg }
        if (oldIdx != -1) slots[oldIdx] = null

        slots[toIndex] = pkg
        persistSlots()
    }

    fun nearestFreeCardRow(targetRow: Int, rowsToShow: Int, ignore: PhoneCardPlacement): Int? {
        val minRow = 0
        val maxRow = rowsToShow - 2
        val clamped = targetRow.coerceIn(minRow, maxRow)

        if (isAreaFreeForCard(clamped, rowsToShow, ignore)) return clamped

        for (delta in 1..maxRow) {
            val up = clamped - delta
            val down = clamped + delta
            if (up >= minRow && isAreaFreeForCard(up, rowsToShow, ignore)) return up
            if (down <= maxRow && isAreaFreeForCard(down, rowsToShow, ignore)) return down
        }
        return null
    }

    fun tryPlaceCard(type: PhoneCardType, rowsToShow: Int) : Boolean {
        val visibleSlots = rowsToShow * COLS
        if (rowsToShow < cardSpanY) return false

        val occ = BooleanArray(visibleSlots) { false }

        // app slotok
        for (i in 0 until visibleSlots) {
            if (slots.getOrNull(i) != null) occ[i] = true
        }

        // meglévő kártyák (mind 2x4)
        fun markCard(r: Int, c: Int) {
            for (dy in 0 until cardSpanY) {
                for (dx in 0 until cardSpanX) {
                    val rr = r + dy
                    val cc = c + dx
                    if (rr in 0 until rowsToShow && cc in 0 until COLS) {
                        val idx = rr * COLS + cc
                        if (idx in 0 until visibleSlots) occ[idx] = true
                    }
                }
            }
        }
        cards.forEach { markCard(it.row, it.col) }

        // keresés: csak col=0
        for (r in 0..(rowsToShow - cardSpanY)) {
            val c = 0
            var ok = true
            for (dy in 0 until cardSpanY) {
                for (dx in 0 until cardSpanX) {
                    val idx = (r + dy) * COLS + (c + dx)
                    if (idx !in 0 until visibleSlots || occ[idx]) { ok = false; break }
                }
                if (!ok) break
            }
            if (ok) {
                cards.add(PhoneCardPlacement(type, r, c))
                persistCards()
                return true
            }
        }
        return false
    }

    // ==========================
    // ✅ ROOT LAYOUT + GLOBAL POINTER TRACKING
    // ==========================
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
        // Ambient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientOverlay)
        )

        // ✅ GLOBAL DRAG TRACKER + DROP (root coords!)
        if (dragging != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10_000f)
                    .pointerInput(dragging) {
                        awaitPointerEventScope {
                            while (dragging != null) {
                                val event = awaitPointerEvent(PointerEventPass.Final)

                                // 1) frissítjük a pozíciót az aktív ujjal (ha tudjuk), különben az elsővel
                                val moveChange = dragPointerId?.let { id ->
                                    event.changes.firstOrNull { it.id == id }
                                } ?: event.changes.firstOrNull()

                                if (moveChange != null) {
                                    dragPointerPx = moveChange.position
                                    hasDragPointer = true
                                }

                                // 2) DROP csak valódi "felengedés" eseményre
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
                                        val best = if (targetIdx != null) nearestFreeSlot(targetIdx, visibleSlots) else null

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

        val btnH = 58.dp
        val bottomGap = 10.dp
        val bottomReserved = btnH + bottomGap

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

            // ===== GRID =====
            @Suppress("UnusedBoxWithConstraintsScope")
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = bottomReserved),
                contentAlignment = Alignment.Center
            ) {
                val padX = 6.dp
                val padY = 2.dp

                val minGapH = 10.dp
                val minGapV = 12.dp

                val availableW = maxWidth - padX * 2
                val availableH = maxHeight - padY * 2

                // 1) cella szélességből
                val cellSizeFromWidth = (availableW - minGapH * (COLS - 1)) / COLS
                val cellSize = if (cellSizeFromWidth > availableH) availableH else cellSizeFromWidth

                SideEffect {
                    cellSizePx = with(density) { cellSize.toPx() }
                    gapHPx = with(density) { minGapH.toPx() }
                    gapVPx = with(density) { minGapV.toPx() }
                }

                // ✅ AddStuffPopup-hoz
                SideEffect { phoneCellDp = cellSize }

                val usedW = cellSize * COLS + minGapH * (COLS - 1)
                SideEffect { gridUsedWidth = usedW }

                // 2) sorok
                val fitsRowsFloat = (availableH.value + minGapV.value) / (cellSize.value + minGapV.value)
                val rowsToShow = floor(fitsRowsFloat).toInt().coerceIn(1, MAX_ROWS)
                SideEffect { rowsToShowState = rowsToShow }

                val visibleSlots = rowsToShow * COLS

                // ha rejtett részben van app, próbáljuk felhúzni üres helyre
                val hiddenOccupied = slots
                    .drop(visibleSlots)
                    .withIndex()
                    .filter { it.value != null }
                    .map { it.index to it.value }

                LaunchedEffect(visibleSlots, hiddenOccupied.size) {
                    var moved = false
                    for ((idx, id) in hiddenOccupied) {
                        val free = (0 until visibleSlots).firstOrNull { slots[it] == null }
                        if (free != null) {
                            slots[free] = id
                            slots[idx] = null
                            moved = true
                        }
                    }
                    if (moved) persistSlots()
                }

                // GRID + Cards overlay container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = padX, vertical = padY)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            gridTopLeftPx = Offset(pos.x, pos.y)
                        }
                ) {
                    fun cellOffsetX(col: Int): Float = with(density) {
                        val cs = cellSize.toPx()
                        val gh = minGapH.toPx()
                        col * (cs + gh)
                    }
                    fun cellOffsetY(row: Int): Float = with(density) {
                        val cs = cellSize.toPx()
                        val gv = minGapV.toPx()
                        row * (cs + gv)
                    }

                    val cardW = (cellSize * 4 + minGapH * 3)
                    val cardH = (cellSize * 2 + minGapV * 1)

                    // =========================
                    // 1) GRID IKONOK (HÁTUL)
                    // =========================
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f),
                        verticalArrangement = Arrangement.spacedBy(minGapV)
                    ) {
                        for (r in 0 until rowsToShow) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(minGapH, Alignment.CenterHorizontally)
                            ) {
                                for (c in 0 until COLS) {
                                    val idx = r * COLS + c
                                    val id = slots.getOrNull(idx)
                                    val isEmpty = (id == null)

                                    val coveredByCard = cards.any { card ->
                                        card.col == 0 &&
                                                r in card.row until (card.row + cardSpanY) &&
                                                c in card.col until (card.col + cardSpanX)
                                    }

                                    HomeSlot(
                                        id = id,
                                        label = resolveLabelForSlot(id),
                                        icon = resolveIconForSlot(id),
                                        isEmpty = isEmpty,
                                        showPlaceholder = (editMode) && !coveredByCard,
                                        modifier = Modifier.size(cellSize),

                                        showDelete = editMode && id != null && !coveredByCard,
                                        onDelete = { if (id != null) removeFromHome(id) },

                                        // drag: app a homescreenen
                                        canDrag = !coveredByCard && id != null,
                                        onStartDrag = {
                                            if (id == null) return@HomeSlot

                                            // módok kizárása
                                            homeQuickMenuOpen = false
                                            addStuffOpen = false
                                            drawerOpen = false
                                            clearPlaceError()

                                            // edit mód ON + drag indul
                                            editMode = true
                                            suppressBackgroundLongPress = true

                                            dragging = DragPayload.App(pkg = id, fromIndex = idx)
                                            hasDragPointer = false
                                        },

                                        onClick = {
                                            if (editMode) return@HomeSlot
                                            if (coveredByCard) return@HomeSlot
                                            if (id != null) launch(id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // =========================
                    // 2) KÁRTYÁK (FELÜL)
                    // =========================
                    cards.forEach { card ->
                        if (card.col != 0) return@forEach
                        if (card.row < 0 || card.row + 1 >= rowsToShow) return@forEach

                        val ox = cellOffsetX(card.col).roundToInt()
                        val oy = cellOffsetY(card.row).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(ox, oy) }
                                .width(cardW)
                                .height(cardH)
                                .zIndex(1f)
                                .pointerInput(card, editMode, rowsToShow) {
                                    if (editMode) {
                                        // kártya drag a saját logikájával
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragging = DragPayload.Card(card)
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consumeAllChanges()

                                                val stepY = cellSizePx + gapVPx
                                                val deltaRows = (dragAmount.y / stepY)

                                                // itt csak "érzésre" mozgatunk: a végleges snap dragEnd-ben lesz
                                                val targetRow = (card.row + deltaRows).roundToInt()
                                                val bestRow = nearestFreeCardRow(
                                                    targetRow = targetRow,
                                                    rowsToShow = rowsToShow,
                                                    ignore = card
                                                )
                                                if (bestRow != null && bestRow != card.row) {
                                                    cards.remove(card)
                                                    cards.add(card.copy(row = bestRow, col = 0))
                                                    persistCards()
                                                }
                                            },
                                            onDragEnd = {
                                                stopDrag()
                                            },
                                            onDragCancel = { stopDrag() }
                                        )
                                    }
                                }
                        ) {
                            PhoneHomeCard(
                                type = card.type,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (editMode) {
                                DeleteBadge(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    onClick = {
                                        cards.remove(card)
                                        persistCards()
                                    }
                                )
                            }
                        }
                    }

                    // =========================
                    // 3) DRAG PREVIEW (LEBEGŐ)
                    // =========================
                    val drag = dragging
                    if (drag is DragPayload.App && hasDragPointer) {
                        val localX = (dragPointerPx.x - gridTopLeftPx.x).roundToInt()
                        val localY = (dragPointerPx.y - gridTopLeftPx.y).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(localX - 24, localY - 24) }
                                .size(48.dp)
                                .zIndex(999f)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) { }

                            Text(
                                text = "⠿",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                // ========= ADD STUFF (Cards/Widgets) =========
                if (addStuffOpen) {
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
                            val ok = tryPlaceCard(t, rowsToShow)
                            if (!ok) {
                                addError = "Nincs elég szabad hely (2×4) a képernyőn."
                                return@AddStuffPopup
                            }
                            addStuffOpen = false
                            selectedCard = null
                            addError = null
                        },
                        pm = pm,
                        cellDp = phoneCellDp,
                        onPickWidget = { _, _, _ ->
                            addStuffOpen = false
                        }
                    )
                }
            }
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

        // ========= ÜRES HÁTTÉR QUICK MENU =========
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

        // ========= DRAWER POPUP =========
        AnimatedVisibility(
            visible = drawerOpen || drawerDragActive,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120))
        ) {
            val drawerAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (drawerDragActive) 0f else 1f,
                animationSpec = tween(durationMillis = 140),
                label = "drawerAlpha"
            )

            val drawerSlide by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (drawerDragActive) 40f else 0f, // px-szerű érzet, lentebb állítjuk dp-be
                animationSpec = tween(durationMillis = 140),
                label = "drawerSlide"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (drawerDragActive) 0f else 0.45f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (!drawerDragActive) drawerOpen = false },
                        onLongClick = { if (!drawerDragActive) drawerOpen = false }
                    )
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1020).copy(alpha = 0.96f)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer(alpha = drawerAlpha)
                        .offset(y = (drawerSlide).dp)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 10.dp)
                        .heightIn(min = 420.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp)) {

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "App lista",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Bezár",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { drawerOpen = false },
                                        onLongClick = { drawerOpen = false }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        TextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Keresés…", color = Color.White.copy(alpha = 0.45f)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.White
                            )
                        )

                        Spacer(Modifier.height(12.dp))
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(12.dp))

                        LazyColumn(
                            state = drawerListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(drawerEntries, key = { idx, e ->
                                when (e) {
                                    is DrawerEntry.Header -> "H:${e.letter}:$idx"
                                    is DrawerEntry.App -> "A:${e.app.packageName}"
                                }
                            }) { _, entry ->
                                when (entry) {
                                    is DrawerEntry.Header -> {
                                        DrawerHeader(letter = entry.letter, selected = (entry.letter == activeLetter))
                                    }
                                    is DrawerEntry.App -> {
                                        DrawerRow(
                                            app = entry.app,
                                            letter = entry.letter,
                                            drawerDragActive = drawerDragActive,
                                            onClick = {
                                                if (drawerDragActive) return@DrawerRow
                                                drawerOpen = false
                                                launch(entry.app.packageName)
                                            },
                                            onStartDrag = { rootPointer ->
                                                dragPointerId = null
                                                hasDragPointer = false

                                                homeQuickMenuOpen = false
                                                addStuffOpen = false
                                                clearPlaceError()

                                                editMode = true
                                                suppressBackgroundLongPress = true

                                                dragging = DragPayload.App(pkg = entry.app.packageName, fromIndex = -1)
                                                hasDragPointer = true
                                                dragPointerPx = rootPointer

                                                // ✅ drawer marad nyitva, csak “elrejtjük” animációval
                                                drawerDragActive = true
                                            },
                                            onDragMove = { rootPointer ->
                                                hasDragPointer = true
                                                dragPointerPx = rootPointer
                                            },
                                            onEndDrag = { rootPointer ->
                                                hasDragPointer = true
                                                dragPointerPx = rootPointer

                                                // DROP
                                                val payload = dragging as? DragPayload.App
                                                if (payload != null) {
                                                    val visibleSlots = rowsToShowState * COLS
                                                    val targetIdx = slotIndexFromPointer(dragPointerPx)
                                                    val best = if (targetIdx != null) nearestFreeSlot(targetIdx, visibleSlots) else null
                                                    if (best != null) {
                                                        moveAppToIndex(payload.pkg, best, visibleSlots)
                                                        clearPlaceError()
                                                    } else {
                                                        placeError = "Nincs szabad slot."
                                                    }
                                                }

                                                stopDrag()
                                                suppressBackgroundLongPress = false

                                                // MOST zárjuk be ténylegesen a drawert
                                                drawerDragActive = false
                                                drawerOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneHomeCard(
    type: PhoneCardType,
    modifier: Modifier = Modifier
) {
    when (type) {
        PhoneCardType.CALENDAR -> {
            CalendarPanelCard(
                modifier = modifier,
                registerKeyHandler = { },
                focusRequester = null,
                vibrationEnabled = true
            )
        }
        PhoneCardType.MUSIC -> {
            MusicControlPanelCard(
                modifier = modifier,
                registerKeyHandler = { },
                focusRequester = null,
                vibrationEnabled = true
            )
        }
        PhoneCardType.NOTIFICATIONS -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(22.dp),
                modifier = modifier
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Értesítések",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleMenu(
    title: String,
    onDismiss: () -> Unit,
    items: List<Pair<String, () -> Unit>>
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
                onLongClick = onDismiss
            )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 320.dp)
                .padding(18.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))

                items.forEach { (label, act) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = act,
                                onLongClick = act
                            )
                    ) {
                        Box(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddStuffPopup(
    tab: Int,
    onTabChange: (Int) -> Unit,
    selectedCard: PhoneCardType?,
    onSelectCard: (PhoneCardType) -> Unit,
    errorText: String?,
    onCancel: () -> Unit,
    onConfirmAddCard: (rowsToShow: Int) -> Unit,
    pm: PackageManager,
    cellDp: Dp,
    onPickWidget: (provider: AppWidgetProviderInfo, spanX: Int, spanY: Int) -> Unit
) {
    val context = LocalContext.current
    val widgetManager = remember { AppWidgetManager.getInstance(context) }

    val pickerState = rememberWidgetPickerState(
        pm = pm,
        appWidgetManager = widgetManager,
        cellWidthDp = cellDp,
        cellHeightDp = cellDp,
        onPick = { provider, sx, sy -> onPickWidget(provider, sx, sy) },
        onBack = { onCancel() },
        vibrationEnabled = true
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
                onLongClick = onCancel
            )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1020).copy(alpha = 0.92f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(18.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TabPill(
                        text = "Kártyák",
                        selected = tab == 0,
                        onClick = { onTabChange(0) }
                    )
                    Spacer(Modifier.width(10.dp))
                    TabPill(
                        text = "Widgetek",
                        selected = tab == 1,
                        onClick = { onTabChange(1) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Bezár",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCancel,
                                onLongClick = onCancel
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (tab == 0) {
                    Text(
                        text = "Válassz kártyát:",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))

                    CardChoiceRow("Naptár", selectedCard == PhoneCardType.CALENDAR) { onSelectCard(PhoneCardType.CALENDAR) }
                    CardChoiceRow("Zene", selectedCard == PhoneCardType.MUSIC) { onSelectCard(PhoneCardType.MUSIC) }
                    CardChoiceRow("Értesítések", selectedCard == PhoneCardType.NOTIFICATIONS) { onSelectCard(PhoneCardType.NOTIFICATIONS) }

                    if (!errorText.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = errorText,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    Row(Modifier.fillMaxWidth()) {
                        ActionPill(
                            text = "Mégse",
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        ActionPill(
                            text = "Hozzáadás",
                            onClick = { onConfirmAddCard(MAX_ROWS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 420.dp, max = 620.dp)
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
}

@Composable
private fun TabPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .height(36.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White.copy(alpha = if (selected) 0.95f else 0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CardChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f)
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (selected) "✓" else "",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .height(40.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DeleteBadge(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .size(22.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "X",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HomeSlot(
    id: String?,
    label: String,
    icon: Drawable?,
    isEmpty: Boolean,
    showPlaceholder: Boolean,
    showDelete: Boolean,
    onDelete: () -> Unit,

    canDrag: Boolean,
    onStartDrag: () -> Unit,

    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isInvisibleEmpty = isEmpty && !showPlaceholder

    val bg = when {
        isInvisibleEmpty -> Color.Transparent
        isEmpty && showPlaceholder -> Color.White.copy(alpha = 0.06f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val base = if (isInvisibleEmpty) modifier else modifier.then(
        if (id != null && canDrag) {
            Modifier.pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onStartDrag() },
                    onDrag = { change, _ -> change.consumeAllChanges() },
                    onDragEnd = { /* drop-ot a GLOBAL overlay intézi */ },
                    onDragCancel = { /* drop-ot a GLOBAL overlay intézi */ }
                )
            }
        } else {
            Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = { }
            )
        }
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(22.dp),
        modifier = base
    ) {
        if (isInvisibleEmpty) return@Card

        Box(Modifier.fillMaxSize()) {
            if (showDelete && id != null) {
                DeleteBadge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    onClick = onDelete
                )
            }

            if (!isEmpty && icon != null) {
                val bmp = remember(icon) { drawableToBitmap(icon) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.align(Alignment.Center).offset(y = (-10).dp).size(34.dp)
                    )
                }
            }

            if (!isEmpty) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp, start = 10.dp, end = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    letter: String,
    selected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = letter,
            color = Color.White.copy(alpha = if (selected) 0.95f else 0.70f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(10.dp))
        Divider(
            color = Color.White.copy(alpha = 0.10f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DrawerRow(
    app: AppItem,
    letter: String,
    drawerDragActive: Boolean,
    onClick: () -> Unit,
    onStartDrag: (rootPointer: Offset) -> Unit,
    onDragMove: (rootPointer: Offset) -> Unit,
    onEndDrag: (rootPointer: Offset) -> Unit
) {
    var rowTopLeftPx by remember { mutableStateOf(Offset.Zero) }

    // ✅ ezt hiányoltuk: az utolsó ismert root pointer pozíció
    var lastRootPointer by remember { mutableStateOf(Offset.Zero) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (drawerDragActive) Color.Transparent else Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                rowTopLeftPx = Offset(pos.x, pos.y)
            }
            .pointerInput(app.packageName) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        val root = rowTopLeftPx + local
                        lastRootPointer = root
                        onStartDrag(root)
                    },
                    onDrag = { change, _ ->
                        change.consumeAllChanges()

                        val root = rowTopLeftPx + change.position
                        lastRootPointer = root
                        onDragMove(root)
                    },
                    onDragEnd = {
                        // ✅ MOST már az ujj utolsó helyével dobunk
                        onEndDrag(lastRootPointer)
                    },
                    onDragCancel = {
                        onEndDrag(lastRootPointer)
                    }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!drawerDragActive) onClick() },
                onLongClick = { /* a pointerInput kezeli */ }
            )
    ) {
        if (drawerDragActive) {
            // drag alatt ne rajzoljuk ki a sort (vizuálisan "eltűnt")
            return@Card
        }

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(app.icon) { drawableToBitmap(app.icon) }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text("•", color = Color.White.copy(alpha = 0.35f), fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = app.label,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = letter,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

private fun drawableToBitmap(d: Drawable?): Bitmap? {
    if (d == null) return null
    return try {
        val w = d.intrinsicWidth.coerceAtLeast(1)
        val h = d.intrinsicHeight.coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, c.width, c.height)
        d.draw(c)
        b
    } catch (_: Throwable) {
        null
    }
}