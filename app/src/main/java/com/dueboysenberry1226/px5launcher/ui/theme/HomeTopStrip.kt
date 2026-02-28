@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.data.TopItem

@Composable
fun HomeTopStrip(
    items: List<TopItem>,
    displayIndex: Int,
    pinned: Set<String>,
    stripState: LazyListState,
    fling: androidx.compose.foundation.gestures.FlingBehavior,
    showSelection: Boolean = true,
    modifier: Modifier = Modifier,
    onSelectIndex: (Int) -> Unit = {},
    onActivate: (TopItem) -> Unit = {},
    onLongPress: (TopItem) -> Unit = {},
    onSelectionTick: () -> Unit = {} // ✅ új: haptic tick callback
) {
    // ✅ tick csak akkor, ha tényleg változott a kijelölés (és ne az első rendernél)
    var lastIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(displayIndex, showSelection) {
        if (!showSelection) return@LaunchedEffect
        if (lastIndex == -1) {
            lastIndex = displayIndex
            return@LaunchedEffect
        }
        if (displayIndex != lastIndex) {
            lastIndex = displayIndex
            onSelectionTick()
        }
    }

    LazyRow(
        state = stripState,
        flingBehavior = fling,
        userScrollEnabled = false, // NEM DRAGGABLE
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        itemsIndexed(items) { index, item ->
            val isSelected = showSelection && (index == displayIndex)

            val selectedLabel =
                if (showSelection) (items.getOrNull(displayIndex) as? TopItem.App)?.app?.label else null

            // felirat a kijelölt UTÁNI első nem-spacer elem alatt legyen
            val labelTargetIndex = remember(displayIndex, items.size) {
                var t = displayIndex + 1
                while (t <= items.lastIndex && items[t] is TopItem.Spacer) t++
                if (t > items.lastIndex) displayIndex else t
            }

            val underLabel =
                if (selectedLabel != null && index == labelTargetIndex) selectedLabel else null

            fun handleTap() {
                if (!showSelection) {
                    onActivate(item)
                    return
                }
                if (index == displayIndex) onActivate(item) else onSelectIndex(index)
            }

            fun handleLong() {
                if (!showSelection) {
                    onLongPress(item)
                    return
                }
                if (index == displayIndex) onLongPress(item) else onSelectIndex(index)
            }

            when (item) {
                is TopItem.App -> TopTile(
                    label = item.app.label,
                    icon = item.app.iconBitmap,
                    selected = isSelected,
                    pinned = item.app.packageName in pinned,
                    underLabel = underLabel,
                    onClick = ::handleTap,
                    onLongPress = ::handleLong
                )

                is TopItem.AllApps -> TopAllAppsTile(
                    selected = isSelected,
                    underLabel = underLabel,
                    onClick = ::handleTap,
                    onLongPress = ::handleLong
                )

                is TopItem.Spacer -> Spacer(
                    modifier = Modifier.requiredSize(101.dp)
                )
            }
        }
    }
}

@Composable
private fun TopTile(
    label: String,
    icon: Bitmap?,
    selected: Boolean,
    pinned: Boolean,
    underLabel: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    val base = 76.dp
    val maxExtra = 25.dp
    val slot = base + maxExtra

    val extra = if (selected) maxExtra else 0.dp
    val size by animateDpAsState(targetValue = base + extra, label = "topTileSize")

    Box(
        modifier = Modifier
            .width(slot)
            .height(130.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .requiredSize(size)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.07f))
                .then(
                    if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                    else Modifier
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .focusable()
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (selected) 52.dp else 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            if (pinned) {
                Text(
                    text = "★",
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    fontSize = 12.sp
                )
            }
        }

        AnimatedContent(
            targetState = underLabel,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
            label = "UnderLabelFade",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 12.dp, y = (-20).dp)
                .padding(bottom = 6.dp)
        ) { text ->
            if (text != null) {
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TopAllAppsTile(
    selected: Boolean,
    underLabel: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1.0f,
        label = "allAppsScale"
    )

    val shape = RoundedCornerShape(14.dp)

    val base = 76.dp
    val maxExtra = 25.dp
    val slot = base + maxExtra

    Box(
        modifier = Modifier
            .width(slot)
            .height(130.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .requiredSize(base)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f))
                .then(
                    if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                    else Modifier
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▦",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedContent(
            targetState = underLabel,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
            label = "UnderLabelFadeAllApps",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 12.dp, y = (-20).dp)
                .padding(bottom = 6.dp)
        ) { text ->
            if (text != null) {
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}