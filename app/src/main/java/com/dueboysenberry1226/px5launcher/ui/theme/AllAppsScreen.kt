package com.dueboysenberry1226.px5launcher.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dueboysenberry1226.px5launcher.data.LaunchableApp

// ===== ALL APPS GRID ITEMS =====
sealed class AllAppsGridItem {
    data object Back : AllAppsGridItem()
    data class App(val app: LaunchableApp) : AllAppsGridItem()
}

@Composable
fun AllAppsScreen(
    items: List<AllAppsGridItem>,
    selectedIndex: Int,
    columns: Int,
    onSelectChange: (Int) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onBack: () -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(selectedIndex, items.size) {
        if (items.isNotEmpty()) {
            gridState.animateScrollToItem(selectedIndex.coerceIn(0, items.lastIndex))
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp),
        modifier = modifier
    ) {
        itemsIndexed(items) { i, item ->
            when (item) {
                is AllAppsGridItem.Back -> {
                    BackToHomeTile(
                        selected = (i == selectedIndex),
                        onClick = onBack
                    )
                }

                is AllAppsGridItem.App -> {
                    val a = item.app
                    MiniGridTile(
                        label = a.label,
                        icon = a.iconBitmap,
                        selected = (i == selectedIndex),
                        onClick = { onLaunch(a) },
                        onLongPress = {
                            onSelectChange(i)
                            onLongPress(a)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniGridTile(
    label: String,
    icon: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        label = "gridTileScale"
    )

    val bgAlpha = if (selected) 0.11f else 0.06f

    Box(
        modifier = Modifier
            .height(94.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = bgAlpha)),
            modifier = Modifier
                .height(86.dp)
                .fillMaxWidth()
                .scale(scale)
                .then(
                    if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                    else Modifier
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
        ) {
            Box(Modifier.fillMaxSize().padding(10.dp)) {
                if (icon != null) {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Text(
                    text = label,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 58.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackToHomeTile(
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        label = "backTileScale"
    )

    val bgAlpha = if (selected) 0.11f else 0.06f

    Box(
        modifier = Modifier
            .height(94.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = bgAlpha)),
            modifier = Modifier
                .height(86.dp)
                .fillMaxWidth()
                .scale(scale)
                .then(
                    if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                    else Modifier
                )
                .combinedClickable(onClick = onClick, onLongClick = {})
        ) {
            Box(Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                    text = "←  Főmenü",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}
