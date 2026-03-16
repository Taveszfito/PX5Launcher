@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import com.dueboysenberry1226.px5launcher.ui.theme.WallpaperGlassSurface
import com.dueboysenberry1226.px5launcher.ui.theme.PhoneGlass
import android.content.res.Configuration
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import com.dueboysenberry1226.px5launcher.ui.theme.CalendarPanelCard
import com.dueboysenberry1226.px5launcher.ui.theme.MusicControlPanelCard

@Composable
private fun PortraitGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    baseContainerColor: Color,
    borderColor: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val isPortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    WallpaperGlassSurface(
        modifier = modifier,
        shape = shape,
        baseContainerColor = baseContainerColor,
        borderColor = borderColor,
        borderWidth = if (borderColor.alpha > 0f) 1.dp else 0.dp,
        contentPadding = contentPadding,
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
            PortraitGlassCard(
                modifier = modifier,
                shape = RoundedCornerShape(22.dp),
                baseContainerColor = Color.White.copy(alpha = 0.10f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                ) {
                    Text(
                        text = stringResource(R.string.notifications_title),
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
                text = stringResource(R.string.delete_badge_text),
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
        isEmpty -> Color.White.copy(alpha = 0.06f)
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
                                change.consume()
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

    if (isInvisibleEmpty) {
        Box(modifier = base)
        return
    }

    PortraitGlassCard(
        modifier = base,
        shape = RoundedCornerShape(22.dp),
        baseContainerColor = bg
    ) {
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
                            change.consume()
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
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = DividerDefaults.Thickness,
            color = Color.White.copy(alpha = 0.10f)
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

    val rowModifier = Modifier
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
                val pointerId = down.id
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

    PortraitGlassCard(
        modifier = rowModifier,
        shape = RoundedCornerShape(18.dp),
        baseContainerColor = if (drawerDragActive) {
            Color.Transparent
        } else {
            Color.White.copy(alpha = 0.08f)
        }
    ) {
        if (drawerDragActive) return@PortraitGlassCard

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
                    text = stringResource(R.string.app_icon_fallback_symbol),
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