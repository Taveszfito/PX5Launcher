@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.dueboysenberry1226.px5launcher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
 *  -2 = semmi nincs kijelölve (TOPBAR vezérlés közben)
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
    modifier: Modifier = Modifier,
    topbarFocusRequester: FocusRequester? = null
) {
    val cols = columns.coerceIn(2, 5)
    val gridState = rememberLazyGridState()

    val apps = remember(items) {
        val out = ArrayList<LaunchableApp>()
        for (it in items) if (it is AllAppsGridItem.App) out.add(it.app)
        out
    }
    val appCount = apps.size
    if (appCount == 0) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    // selectedIndex lehet -2 (none), -1 (Back) vagy 0..appCount-1
    val safeSelected = when {
        selectedIndex == SEL_NONE -> SEL_NONE
        selectedIndex < SEL_BACK -> SEL_NONE
        selectedIndex >= appCount -> appCount - 1
        else -> selectedIndex
    }

    // =========================================================
    // ✅ RÉGI / BEVÁLT AUTO-SCROLL:
    // Minden selected változásnál görgessünk rá az itemre.
    // (Back/NONE esetén nem scrollozunk)
    // =========================================================
    LaunchedEffect(safeSelected, appCount) {
        if (safeSelected >= 0 && safeSelected < appCount) {
            gridState.animateScrollToItem(safeSelected)
        }
    }

    Row(
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top
    ) {
        // ===== BAL OLDALI PICI BACK "GOMB" (NEM FÓKUSZOS) =====
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
                onClick = onBack
            )
        }

        // ===== APPS GRID =====
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
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(apps) { index, app ->
                val col = index % cols
                val isFirstColumn = (col == 0)

                AppTile(
                    app = app,
                    isSelected = (safeSelected == index),
                    iconSize = adaptiveIconSize(cols),
                    isFirstColumn = isFirstColumn,
                    onBackSelect = { onSelectChange(SEL_BACK) },
                    onBack = onBack,
                    onFocus = { onSelectChange(index) },
                    onClick = {
                        onSelectChange(index)
                        onLaunch(app)
                    },
                    onLongPress = {
                        onSelectChange(index)
                        onLongPress(app)
                    }
                )
            }
        }
    }
}

@Composable
private fun BackMiniTile(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

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
    onBack: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        label = "appTileScale"
    )
    val glowAlpha = if (isSelected) 0.35f else 0f

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(shape)
            .onPreviewKeyEvent { e ->
                if (e.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                // BALRA első oszlopban: először Back kijelölés
                if (isFirstColumn && e.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT) {
                    onBackSelect()
                    return@onPreviewKeyEvent true
                }

                false
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.08f + glowAlpha),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .then(
                if (isSelected) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), shape)
                else Modifier
            )
            .combinedClickable(
                onClick = {
                    onFocus()
                    onClick()
                },
                onLongClick = {
                    onFocus()
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

            app.iconBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape((iconSize.value / 4f).dp))
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