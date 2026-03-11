@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import com.dueboysenberry1226.px5launcher.ui.CalendarPanelCard
import com.dueboysenberry1226.px5launcher.ui.MusicControlPanelCard

@Composable
internal fun PhoneHomeCard(
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
internal fun DeleteBadge(
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
internal fun HomeSlot(
    id: String?,
    label: String,
    icon: Drawable?,
    isEmpty: Boolean,
    showPlaceholder: Boolean,
    showDelete: Boolean,
    onDelete: () -> Unit,

    canDrag: Boolean,
    onStartDrag: (rootPointer: Offset) -> Unit,
    onDragMove: (rootPointer: Offset) -> Unit,
    onEndDrag: (rootPointer: Offset) -> Unit,

    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isInvisibleEmpty = isEmpty && !showPlaceholder
    var slotTopLeftPx by remember { mutableStateOf(Offset.Zero) }
    var lastRootPointer by remember { mutableStateOf(Offset.Zero) }

    val bg = when {
        isInvisibleEmpty -> Color.Transparent
        isEmpty && showPlaceholder -> Color.White.copy(alpha = 0.06f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val base = if (isInvisibleEmpty) {
        modifier
    } else {
        modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                slotTopLeftPx = Offset(pos.x, pos.y)
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = { }
            )
            .then(
                if (id != null && canDrag) {
                    Modifier.pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                val root = slotTopLeftPx + local
                                lastRootPointer = root
                                onStartDrag(root)
                            },
                            onDrag = { change, _ ->
                                change.consumeAllChanges()
                                val root = slotTopLeftPx + change.position
                                lastRootPointer = root
                                onDragMove(root)
                            },
                            onDragEnd = {
                                onEndDrag(lastRootPointer)
                            },
                            onDragCancel = {
                                onEndDrag(lastRootPointer)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(22.dp),
        modifier = base
    ) {
        if (isInvisibleEmpty) return@Card

        Box(Modifier.fillMaxSize()) {
            if (showDelete && id != null) {
                DeleteBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    onClick = onDelete
                )
            }

            if (!isEmpty && icon != null) {
                val bmp = remember(icon) { drawableToBitmap(icon) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = (-10).dp)
                            .size(34.dp)
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp, start = 10.dp, end = 10.dp)
                )
            }
        }
    }
}

/**
 * ✅ Helper: Widget doboz long-press drag felület.
 * (A drop-ot a global overlay intézi, ugyanúgy mint az appoknál.)
 */
@Composable
internal fun WidgetDragSurface(
    enabled: Boolean,
    key: Any,
    onStartDrag: (rootPointer: Offset) -> Unit,
    onDragMove: (rootPointer: Offset) -> Unit,
    onEndDrag: (rootPointer: Offset) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var topLeftPx by remember { mutableStateOf(Offset.Zero) }
    var lastRootPointer by remember { mutableStateOf(Offset.Zero) }

    val m = modifier
        .onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            topLeftPx = Offset(pos.x, pos.y)
        }
        .then(
            if (!enabled) {
                Modifier
            } else {
                Modifier.pointerInput(key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            val root = topLeftPx + local
                            lastRootPointer = root
                            onStartDrag(root)
                        },
                        onDrag = { change, _ ->
                            change.consumeAllChanges()
                            val root = topLeftPx + change.position
                            lastRootPointer = root
                            onDragMove(root)
                        },
                        onDragEnd = {
                            onEndDrag(lastRootPointer)
                        },
                        onDragCancel = {
                            onEndDrag(lastRootPointer)
                        }
                    )
                }
            }
        )

    Box(modifier = m, content = content)
}

@Composable
internal fun DrawerHeader(
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
internal fun DrawerRow(
    app: AppItem,
    letter: String,
    drawerDragActive: Boolean,
    onClick: () -> Unit,
    onStartDrag: (rootPointer: Offset) -> Unit,
    onDragMove: (rootPointer: Offset) -> Unit,
    onEndDrag: (rootPointer: Offset) -> Unit
) {
    var rowTopLeftPx by remember { mutableStateOf(Offset.Zero) }
    var lastRootPointer by remember { mutableStateOf(Offset.Zero) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (drawerDragActive) {
                Color.Transparent
            } else {
                Color.White.copy(alpha = 0.08f)
            }
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
                val moveSlop = 18.dp.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragStarted = false
                    var longPressReached = false
                    var pointerId = down.id
                    var currentPos = down.position
                    lastRootPointer = rowTopLeftPx + currentPos

                    val longPress = awaitLongPressOrCancellation(down.id)

                    if (longPress != null) {
                        longPressReached = true
                        currentPos = longPress.position
                        lastRootPointer = rowTopLeftPx + currentPos
                    }

                    if (!longPressReached) {
                        return@awaitEachGesture
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                        if (!change.pressed) {
                            if (dragStarted) {
                                onEndDrag(lastRootPointer)
                            }
                            break
                        }

                        currentPos = change.position
                        val root = rowTopLeftPx + currentPos
                        lastRootPointer = root

                        val moved = currentPos - down.position
                        val movedDistance = kotlin.math.hypot(
                            moved.x.toDouble(),
                            moved.y.toDouble()
                        ).toFloat()

                        if (!dragStarted) {
                            if (movedDistance >= moveSlop) {
                                dragStarted = true
                                onStartDrag(root)
                                change.consume()
                            }
                        } else {
                            onDragMove(root)
                            change.consume()
                        }
                    }
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!drawerDragActive) onClick() },
                onLongClick = { /* a pointerInput kezeli */ }
            )
    ) {
        if (drawerDragActive) return@Card

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
                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 18.sp
                )
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