@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.data.LauncherRepository
import com.dueboysenberry1226.px5launcher.util.computeDominantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.floor

private const val COLS = 4
private const val MAX_ROWS = 7
private const val SLOTS = COLS * MAX_ROWS

private const val SPECIAL_APP_DRAWER = "__PX5_APP_DRAWER__"

private data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

private enum class HoldingMode { NONE, PLACE_FROM_DRAWER, MOVE_EXISTING }

private sealed class DrawerEntry {
    data class Header(val letter: String) : DrawerEntry()
    data class App(val app: AppItem, val letter: String) : DrawerEntry()
}

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

    fun openAppInfo(pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    suspend fun setAmbientFromApp(app: AppItem?) {
        if (app == null) return
        val bmp = drawableToBitmap(app.icon) ?: return
        val c: Color = computeDominantColor(bmp) // nálad Color-t ad vissza
        ambientColor = if (c.alpha <= 0.01f) Color(0xFF101826) else c
    }

    // ✅ Ambient a legutóbb megnyitott app alapján (recents[0])
    LaunchedEffect(recents, allApps) {
        val pkg = recents.firstOrNull() ?: return@LaunchedEffect
        val app = allApps.firstOrNull { it.packageName == pkg }
        setAmbientFromApp(app)
    }

    // --- Home slots (saveable 28) ---
    val slotsSaver: Saver<MutableList<String?>, List<String?>> = Saver(
        save = { it.toList() },
        restore = { it.toMutableList() }
    )

    val slots = rememberSaveable(saver = slotsSaver) {
        MutableList<String?>(SLOTS) { null }
    }

    // régi mentésből ha valahol benne maradt:
    LaunchedEffect(Unit) {
        val idx = slots.indexOfFirst { it == SPECIAL_APP_DRAWER }
        if (idx != -1) slots[idx] = null
    }

    fun resolveLabelForSlot(id: String?): String {
        return when (id) {
            null -> ""
            SPECIAL_APP_DRAWER -> "App lista"
            else -> allApps.firstOrNull { it.packageName == id }?.label ?: id
        }
    }

    fun resolveIconForSlot(id: String?): Drawable? {
        return when (id) {
            null -> null
            SPECIAL_APP_DRAWER -> null
            else -> allApps.firstOrNull { it.packageName == id }?.icon
        }
    }

    // --- Holding state ---
    var holdingMode by rememberSaveable { mutableStateOf(HoldingMode.NONE) }
    var holdingId by rememberSaveable { mutableStateOf<String?>(null) } // pkg

    fun cancelHolding() {
        holdingMode = HoldingMode.NONE
        holdingId = null
    }

    fun startMoveExisting(id: String) {
        holdingMode = HoldingMode.MOVE_EXISTING
        holdingId = id
    }

    fun placeHeldInto(slotIndex: Int, visibleSlots: Int) {
        val id = holdingId ?: return
        if (slotIndex !in 0 until visibleSlots) return

        val oldIdx = slots.indexOfFirst { it == id }
        if (oldIdx != -1) slots[oldIdx] = null

        slots[slotIndex] = id
        cancelHolding()
    }

    fun removeFromHome(id: String) {
        val idx = slots.indexOfFirst { it == id }
        if (idx != -1) slots[idx] = null
    }

    // --- Drawer popup ---
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    val filteredApps = remember(allApps, search.text) {
        val q = search.text.trim().lowercase(scopeLocale)
        if (q.isEmpty()) allApps
        else allApps.filter { it.label.lowercase(scopeLocale).contains(q) }
    }

    fun letterOf(app: AppItem): String {
        val ch = app.label.trim().firstOrNull()?.uppercaseChar()
        return when {
            ch == null -> "#"
            ch.isLetterOrDigit() -> ch.toString()
            else -> "#"
        }
    }

    // Drawer entries: Header(A) + App rows; így a "betűs" dolog együtt scrolloz
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

    // Drawer list state + aktív betű
    val drawerListState = rememberLazyListState()
    var activeLetter by remember { mutableStateOf<String?>(null) }

    // aktív betű frissítés az első látható APP alapján (csak UI kiemeléshez)
    LaunchedEffect(drawerEntries, drawerListState.firstVisibleItemIndex) {
        val start = drawerListState.firstVisibleItemIndex.coerceIn(0, (drawerEntries.size - 1).coerceAtLeast(0))
        val appEntry = drawerEntries.drop(start).firstOrNull { it is DrawerEntry.App } as? DrawerEntry.App
        activeLetter = appEntry?.letter
    }

    // search változásnál lista elejére
    LaunchedEffect(search.text) {
        if (drawerEntries.isNotEmpty()) drawerListState.scrollToItem(0)
    }

    // --- Home context menu ---
    var menuSlotId by remember { mutableStateOf<String?>(null) }
    fun closeMenu() { menuSlotId = null }

    // --- grid méretek state: hogy az App lista gomb szélessége passzoljon ---
    var gridUsedWidth by remember { mutableStateOf(0.dp) }
    var appListButtonHeight by remember { mutableStateOf(58.dp) }

    // ====== LAYOUT ======
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientOverlay)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Holding hint — kisebb spacer alatta
            val showEmptySlots = (holdingMode != HoldingMode.NONE && holdingId != null)

            if (showEmptySlots) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tedd le egy slotba",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Mégse",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { cancelHolding() },
                                    onLongClick = { cancelHolding() }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            } else {
                Spacer(Modifier.height(4.dp))
            }

            // ====== GRID ======
            @Suppress("UnusedBoxWithConstraintsScope")
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // EZEKET használd, mert már Dp-ben vannak:
                val maxWidth = this.maxWidth
                val maxHeight = this.maxHeight

                val padX = 6.dp
                val padY = 2.dp

                val availableW = maxWidth - padX * 2
                val availableH = maxHeight - padY * 2 - appListButtonHeight - 10.dp

                val cellW = availableW / COLS
                val cellH = availableH / MAX_ROWS

                val cellSize = if (cellW < cellH) cellW else cellH

                val minGapH = 10.dp
                val minGapV = 12.dp

                val usedW = cellSize * COLS + minGapH * (COLS - 1)
                val usedH = cellSize * MAX_ROWS + minGapV * (MAX_ROWS - 1)

                SideEffect {
                    gridUsedWidth = usedW
                }

                val fitsRowsFloat = (availableH.value + minGapV.value) / (cellSize.value + minGapV.value)
                val rowsToShow = floor(fitsRowsFloat).toInt().coerceIn(1, MAX_ROWS)

                val visibleSlots = rowsToShow * COLS
                val hiddenOccupied = slots.drop(visibleSlots).withIndex().filter { it.value != null }.map { it.index to it.value }

                // ha a rejtett részben van app, próbáljuk felhúzni üres helyre
                LaunchedEffect(visibleSlots, hiddenOccupied.size) {
                    for ((idx, id) in hiddenOccupied) {
                        val free = (0 until visibleSlots).firstOrNull { slots[it] == null }
                        if (free != null) {
                            slots[free] = id
                            slots[idx] = null
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = padX, vertical = padY),
                    verticalArrangement = Arrangement.spacedBy(minGapV)
                ) {
                    for (r in 0 until rowsToShow) {
                        Row(horizontalArrangement = Arrangement.spacedBy(minGapH)) {
                            for (c in 0 until COLS) {
                                val idx = r * COLS + c
                                val id = slots.getOrNull(idx)
                                val isEmpty = (id == null)

                                val shouldRenderCard =
                                    (!isEmpty) || showEmptySlots

                                if (shouldRenderCard) {
                                    HomeSlot(
                                        id = id,
                                        label = resolveLabelForSlot(id),
                                        icon = resolveIconForSlot(id),
                                        isEmpty = isEmpty,
                                        showPlaceholder = showEmptySlots,
                                        modifier = Modifier.size(cellSize),
                                        onClick = {
                                            if (showEmptySlots && holdingId != null) {
                                                placeHeldInto(idx, visibleSlots)
                                                return@HomeSlot
                                            }
                                            if (id != null) launch(id)
                                        },
                                        onLongPress = {
                                            if (id == null) return@HomeSlot
                                            menuSlotId = id
                                        }
                                    )
                                } else {
                                    Box(modifier = Modifier.size(cellSize))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ===== App lista gomb: fix blokk, soha nem ér össze a griddel =====
            val btnH = 58.dp
            SideEffect { appListButtonHeight = btnH }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(if (gridUsedWidth.value > 0f) gridUsedWidth else Modifier.fillMaxWidth().let { 0.dp })
                    .then(
                        if (gridUsedWidth.value > 0f) Modifier else Modifier.fillMaxWidth()
                    )
                    .height(btnH)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { drawerOpen = true },
                        onLongClick = { drawerOpen = true } // nem törölhető, nem mozgatható
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

        // ========= HOME LONG-PRESS MENU =========
        if (menuSlotId != null) {
            val id = menuSlotId!!
            val title = resolveLabelForSlot(id)

            PopupMenuCenter(
                title = title,
                onDismiss = { closeMenu() },
                actions = buildList {
                    add("Áthelyezés" to {
                        closeMenu()
                        startMoveExisting(id)
                    })
                    add("App infó" to {
                        closeMenu()
                        openAppInfo(id)
                    })
                    add("Törlés a kezdőképernyőről" to {
                        closeMenu()
                        removeFromHome(id)
                    })
                }
            )
        }

        // ========= DRAWER POPUP =========
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { drawerOpen = false },
                        onLongClick = { drawerOpen = false }
                    )
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1020).copy(alpha = 0.96f)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(min = 420.dp)
                        .navigationBarsPadding()
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
                            Spacer(Modifier.weight(1f))
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
                                            onClick = {
                                                drawerOpen = false
                                                launch(entry.app.packageName)
                                            },
                                            onLongPress = {
                                                holdingMode = HoldingMode.PLACE_FROM_DRAWER
                                                holdingId = entry.app.packageName
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
private fun HomeSlot(
    id: String?,
    label: String,
    icon: Drawable?,
    isEmpty: Boolean,
    showPlaceholder: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val bg = when {
        isEmpty && showPlaceholder -> Color.White.copy(alpha = 0.06f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongPress
        )
    ) {
        Box(Modifier.fillMaxSize()) {

            // ICON középen, picit fentebb
            if (!isEmpty && icon != null) {
                val bmp = remember(icon) { drawableToBitmap(icon) }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = (-10).dp)
                            .size(34.dp)
                    )
                }
            }

            // LABEL alul (csak ha van app)
            if (!isEmpty) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp, start = 10.dp, end = 10.dp)
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
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(app.icon) { drawableToBitmap(app.icon) }
            if (bmp != null) {
                androidx.compose.foundation.Image(
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

            // ✅ betű az app mellett (így együtt scrolloz)
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

@Composable
private fun PopupMenuCenter(
    title: String,
    onDismiss: () -> Unit,
    actions: List<Pair<String, () -> Unit>>
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
                onLongClick = onDismiss
            )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1020).copy(alpha = 0.92f)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 320.dp)
                .padding(18.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(12.dp))

                actions.forEach { (label, act) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(14.dp),
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

private fun drawableToBitmap(d: Drawable?): android.graphics.Bitmap? {
    if (d == null) return null
    return try {
        val w = d.intrinsicWidth.coerceAtLeast(1)
        val h = d.intrinsicHeight.coerceAtLeast(1)
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        d.setBounds(0, 0, c.width, c.height)
        d.draw(c)
        b
    } catch (_: Throwable) {
        null
    }
}