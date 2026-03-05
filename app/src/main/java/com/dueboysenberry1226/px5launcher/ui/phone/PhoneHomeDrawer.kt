@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun PhoneHomeDrawer(
    open: Boolean,
    dragActive: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,

    allApps: List<AppItem>,
    rowsToShowState: Int,

    dragging: DragPayload?,
    setDragging: (DragPayload?) -> Unit,

    setDragPointer: (Offset) -> Unit,
    setHasDragPointer: (Boolean) -> Unit,

    onBeginEditDrag: () -> Unit,
    onEndEditDrag: () -> Unit,

    onLaunch: (String) -> Unit,

    slotIndexFromPointer: (Offset) -> Int?,
    nearestFreeSlot: (Int, Int) -> Int?,
    moveAppToIndex: (String, Int, Int) -> Unit,

    placeErrorSet: (String?) -> Unit,
    clearPlaceError: () -> Unit
) {
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val scopeLocale = remember { Locale.getDefault() }

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

    AnimatedVisibility(
        visible = open || dragActive,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120))
    ) {
        val drawerAlpha by animateFloatAsState(
            targetValue = if (dragActive) 0f else 1f,
            animationSpec = tween(durationMillis = 140),
            label = "drawerAlpha"
        )

        val drawerSlide by animateFloatAsState(
            targetValue = if (dragActive) 40f else 0f,
            animationSpec = tween(durationMillis = 140),
            label = "drawerSlide"
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (dragActive) 0f else 0.45f))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!dragActive) onOpenChange(false) },
                    onLongClick = { if (!dragActive) onOpenChange(false) }
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
                                    onClick = { onOpenChange(false) },
                                    onLongClick = { onOpenChange(false) }
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
                                        drawerDragActive = dragActive,
                                        onClick = {
                                            if (dragActive) return@DrawerRow
                                            onOpenChange(false)
                                            onLaunch(entry.app.packageName)
                                        },
                                        onStartDrag = { rootPointer ->
                                            onBeginEditDrag()

                                            setDragging(DragPayload.App(pkg = entry.app.packageName, fromIndex = -1))
                                            setHasDragPointer(true)
                                            setDragPointer(rootPointer)

                                            // drawer marad "nyitva", csak elrejtjük
                                            onDragActiveChange(true)
                                        },
                                        onDragMove = { rootPointer ->
                                            setHasDragPointer(true)
                                            setDragPointer(rootPointer)
                                        },
                                        onEndDrag = { rootPointer ->
                                            setHasDragPointer(true)
                                            setDragPointer(rootPointer)

                                            val payload = dragging as? DragPayload.App
                                            if (payload != null) {
                                                val visibleSlots = rowsToShowState * COLS
                                                val targetIdx = slotIndexFromPointer(rootPointer)
                                                val best = if (targetIdx != null) nearestFreeSlot(targetIdx, visibleSlots) else null
                                                if (best != null) {
                                                    moveAppToIndex(payload.pkg, best, visibleSlots)
                                                    clearPlaceError()
                                                } else {
                                                    placeErrorSet("Nincs szabad slot.")
                                                }
                                            }

                                            setDragging(null)
                                            onEndEditDrag()

                                            onDragActiveChange(false)
                                            onOpenChange(false)
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