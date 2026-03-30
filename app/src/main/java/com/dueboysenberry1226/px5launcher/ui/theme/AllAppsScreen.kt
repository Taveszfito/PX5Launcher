@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)

package com.dueboysenberry1226.px5launcher.ui.theme

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.data.LaunchableApp

sealed class AllAppsGridItem {
    object Back : AllAppsGridItem()
    data class App(val app: LaunchableApp) : AllAppsGridItem()
}

/**
 * selectedIndex:
 *  -2 = semmi nincs kijelölve (TOPBAR / TOUCH vezérlés közben)
 *  -1 = Back kijelölve
 *   0.. = appok
 */
private const val SEL_NONE = -2
private const val SEL_BACK = -1

@Composable
fun AllAppsScreen(
    items: List<AllAppsGridItem>,
    selectedIndex: Int,              // -2 = none, -1 = Back, 0.. = appok
    columns: Int,
    onSelectChange: (Int) -> Unit,   // -2 = none, -1 = Back, 0.. = appok
    onLaunch: (LaunchableApp) -> Unit,
    onBack: () -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val cols = columns.coerceIn(2, 5)
    val gridState = rememberLazyGridState()

    val apps = remember(items) {
        buildList {
            for (it in items) {
                if (it is AllAppsGridItem.App) add(it.app)
            }
        }
    }
    val appCount = apps.size

    if (appCount == 0) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val safeSelected = when {
        selectedIndex == SEL_NONE -> SEL_NONE
        selectedIndex < SEL_BACK -> SEL_NONE
        selectedIndex >= appCount -> appCount - 1
        else -> selectedIndex
    }

    // Touch vezérlésnél ne maradjon semmi selected.
    val touchClearSelectionModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            if (safeSelected != SEL_NONE) onSelectChange(SEL_NONE)
        }
    }

    // A korábbi fő jank forrás: animateScrollToItem() minden selected lépésnél.
    // Itt csak akkor scrollozunk, ha a selected tényleg kilóg a látható tartományból.
    // Közelre instant scroll, nagy ugrásnál animált scroll.
    LaunchedEffect(safeSelected, appCount) {
        if (safeSelected !in 0 until appCount) return@LaunchedEffect

        val visible = gridState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect

        val firstVisibleIndex = visible.first().index
        val lastVisibleIndex = visible.last().index

        if (safeSelected in firstVisibleIndex..lastVisibleIndex) return@LaunchedEffect

        val distance = when {
            safeSelected < firstVisibleIndex -> firstVisibleIndex - safeSelected
            else -> safeSelected - lastVisibleIndex
        }

        if (distance <= cols) {
            gridState.scrollToItem(safeSelected)
        } else {
            gridState.animateScrollToItem(safeSelected)
        }
    }

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(74.dp)
                .fillMaxHeight()
                .padding(start = 18.dp, top = 14.dp)
                .focusProperties { canFocus = false }
                .focusable(false),
            contentAlignment = Alignment.TopStart
        ) {
            BackMiniTile(
                isSelected = (safeSelected == SEL_BACK),
                onClick = onBack,
                onTouchInteraction = {
                    if (safeSelected != SEL_NONE) onSelectChange(SEL_NONE)
                }
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = gridState,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 32.dp,
                top = 14.dp,
                bottom = 32.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .then(touchClearSelectionModifier)
        ) {
            itemsIndexed(
                items = apps,
                key = { index, app -> "${app.label}#$index" }
            ) { index, app ->
                val col = index % cols
                val isFirstColumn = (col == 0)

                AppTile(
                    app = app,
                    isSelected = (safeSelected == index),
                    iconSize = adaptiveIconSize(cols),
                    isFirstColumn = isFirstColumn,
                    onBackSelect = { onSelectChange(SEL_BACK) },
                    onFocus = { onSelectChange(index) },
                    onTouchInteraction = {
                        if (safeSelected != SEL_NONE) onSelectChange(SEL_NONE)
                    },
                    onClick = { onLaunch(app) },
                    onLongPress = { onLongPress(app) }
                )
            }
        }
    }
}

@Composable
private fun BackMiniTile(
    isSelected: Boolean,
    onClick: () -> Unit,
    onTouchInteraction: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.10f else 1f,
        label = "backMiniScale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(shape)
            .background(Color.White.copy(alpha = if (isSelected) 0.16f else 0.10f))
            .then(
                if (isSelected) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.20f), shape)
            )
            .focusProperties { canFocus = false }
            .focusable(false)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onTouchInteraction()
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = null
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun AppTile(
    app: LaunchableApp,
    isSelected: Boolean,
    iconSize: Dp,
    isFirstColumn: Boolean,
    onBackSelect: () -> Unit,
    onFocus: () -> Unit,
    onTouchInteraction: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        label = "appTileScale"
    )
    val glowAlpha = if (isSelected) 0.35f else 0f

    val backgroundBrush = remember(glowAlpha) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.08f + glowAlpha),
                Color.White.copy(alpha = 0.04f)
            )
        )
    }

    val iconBitmap: ImageBitmap? = remember(app.iconBitmap) {
        app.iconBitmap?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(shape)
            .onPreviewKeyEvent { e ->
                if (e.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                if (isFirstColumn && e.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT) {
                    onBackSelect()
                    return@onPreviewKeyEvent true
                }

                false
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onTouchInteraction()
                }
            }
            .background(backgroundBrush)
            .then(
                if (isSelected) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), shape)
                else Modifier
            )
            .combinedClickable(
                onClick = {
                    onClick()
                },
                onLongClick = {
                    onLongPress()
                }
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            iconBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .clip(RoundedCornerShape((iconSize.value / 4f).dp))
                )
            }

            Text(
                text = app.label,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun adaptiveIconSize(cols: Int): Dp {
    return when (cols) {
        2 -> 96.dp
        3 -> 78.dp
        4 -> 66.dp
        else -> 56.dp
    }
}
