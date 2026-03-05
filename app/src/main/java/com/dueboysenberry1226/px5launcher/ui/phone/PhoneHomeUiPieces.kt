@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
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
                    onDragEnd = { onEndDrag(lastRootPointer) },
                    onDragCancel = { onEndDrag(lastRootPointer) }
                )
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!drawerDragActive) onClick() },
                onLongClick = { /* pointerInput kezeli */ }
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