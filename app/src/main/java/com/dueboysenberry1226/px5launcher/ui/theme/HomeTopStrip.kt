@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
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
import androidx.compose.ui.graphics.ImageBitmap
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
    fling: FlingBehavior,
    showSelection: Boolean = true,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onSelectIndex: (Int) -> Unit = {},
    onActivate: (TopItem) -> Unit = {},
    onLongPress: (TopItem) -> Unit = {},
    onSelectionTick: () -> Unit = {}
) {
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

    val selectedLabel = remember(items, displayIndex, showSelection) {
        if (showSelection) (items.getOrNull(displayIndex) as? TopItem.App)?.app?.label else null
    }

    val labelTargetIndex = remember(displayIndex, items) {
        var t = displayIndex + 1
        while (t <= items.lastIndex && items[t] is TopItem.Spacer) t++
        if (t > items.lastIndex) displayIndex else t
    }

    LazyRow(
        state = stripState,
        flingBehavior = fling,
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        itemsIndexed(
            items = items,
            key = { index, item ->
                when (item) {
                    is TopItem.App -> "app:${item.app.packageName}"
                    is TopItem.AllApps -> "all_apps"
                    is TopItem.Spacer -> "spacer_$index"
                }
            }
        ) { index, item ->
            val isSelected = showSelection && (index == displayIndex)
            val underLabel = if (selectedLabel != null && index == labelTargetIndex) selectedLabel else null

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

    val iconBitmap: ImageBitmap? = remember(icon) {
        icon?.asImageBitmap()
    }

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
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
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

        UnderLabel(
            text = underLabel,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 12.dp, y = (-20).dp)
                .padding(bottom = 6.dp)
        )
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

        UnderLabel(
            text = underLabel,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 12.dp, y = (-20).dp)
                .padding(bottom = 6.dp)
        )
    }
}

@Composable
private fun UnderLabel(
    text: String?,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
        label = "UnderLabelFade",
        modifier = modifier
    ) { value ->
        if (value != null) {
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
