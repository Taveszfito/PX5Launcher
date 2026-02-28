@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dueboysenberry1226.px5launcher.ui.Haptics
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

sealed class VisibleItem {
    data class Header(val pkg: String, val count: Int) : VisibleItem()
    data class Widget(
        val pkg: String,
        val provider: AppWidgetProviderInfo,
        val spanX: Int,
        val spanY: Int
    ) : VisibleItem()
}

class WidgetPickerState internal constructor(
    val pm: PackageManager,
    private val appWidgetManager: AppWidgetManager,
    private val cellWidthDp: Dp,
    private val cellHeightDp: Dp,
    private val onPick: (AppWidgetProviderInfo, Int, Int) -> Unit,
    private val onBack: () -> Unit,
    private val hapticClick: () -> Unit
) {
    var query by mutableStateOf("")
        private set

    var expandedPkgs by mutableStateOf(setOf<String>())
        private set

    var selectedIndex by mutableStateOf(0)
        private set

    val providers: List<AppWidgetProviderInfo> =
        appWidgetManager.installedProviders?.toList() ?: emptyList()

    private val grouped: List<Pair<String, List<AppWidgetProviderInfo>>> by lazy {
        providers
            .filter { it.provider != null }
            .groupBy { it.provider.packageName }
            .toList()
            .sortedBy { (pkg, _) -> safeAppLabel(pm, pkg).lowercase() }
    }

    val filtered: List<Pair<String, List<AppWidgetProviderInfo>>> by derivedStateOf {
        val q = query.trim().lowercase()
        if (q.isBlank()) grouped
        else grouped.mapNotNull { (pkg, list) ->
            val appLabel = safeAppLabel(pm, pkg).lowercase()
            val kept = list.filter { p ->
                val wLabel = safeWidgetLabel(pm, p).lowercase()
                appLabel.contains(q) || wLabel.contains(q) || pkg.lowercase().contains(q)
            }
            if (kept.isEmpty()) null else pkg to kept
        }
    }

    val visibleItems: List<VisibleItem> by derivedStateOf {
        buildList {
            filtered.forEach { (pkg, list) ->
                add(VisibleItem.Header(pkg = pkg, count = list.size))
                if (expandedPkgs.contains(pkg)) {
                    list.sortedBy { safeWidgetLabel(pm, it).lowercase() }.forEach { p ->
                        val (sx, sy) = inferSpan(p, cellWidthDp.value, cellHeightDp.value)
                        add(VisibleItem.Widget(pkg = pkg, provider = p, spanX = sx, spanY = sy))
                    }
                }
            }
        }
    }

    fun updateQuery(v: String) {
        query = v
        selectedIndex = 0
    }

    fun toggleExpanded(pkg: String) {
        // ✅ haptic amikor kinyitsz/összecsuksz egy appot
        hapticClick()
        expandedPkgs = if (expandedPkgs.contains(pkg)) expandedPkgs - pkg else expandedPkgs + pkg
    }

    fun selectIndex(i: Int) {
        selectedIndex = i.coerceIn(0, max(0, visibleItems.lastIndex))
    }

    fun back() {
        // ✅ haptic X / back
        hapticClick()
        onBack()
    }

    fun activateAt(index: Int) {
        val item = visibleItems.getOrNull(index) ?: return
        when (item) {
            is VisibleItem.Header -> toggleExpanded(item.pkg)
            is VisibleItem.Widget -> {
                // ✅ haptic widget kiválasztáskor
                hapticClick()
                onPick(item.provider, item.spanX, item.spanY)
            }
        }
    }

    fun activateSelected() {
        activateAt(selectedIndex)
    }

    fun handleKey(e: KeyEvent): Boolean {
        val nk = e.nativeKeyEvent
        if (nk.action != AndroidKeyEvent.ACTION_DOWN) return false

        if (visibleItems.isEmpty()) {
            if (nk.keyCode == AndroidKeyEvent.KEYCODE_BACK || nk.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE) {
                back()
                return true
            }
            return false
        }

        when (nk.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                selectIndex(selectedIndex - 1)
                return true
            }

            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                selectIndex(selectedIndex + 1)
                return true
            }

            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                activateSelected()
                return true
            }

            AndroidKeyEvent.KEYCODE_BACK,
            AndroidKeyEvent.KEYCODE_ESCAPE -> {
                back()
                return true
            }

            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                val item = visibleItems.getOrNull(selectedIndex)
                if (item is VisibleItem.Widget) {
                    for (i in selectedIndex downTo 0) {
                        if (visibleItems[i] is VisibleItem.Header) {
                            selectIndex(i)
                            return true
                        }
                    }
                } else if (item is VisibleItem.Header) {
                    if (expandedPkgs.contains(item.pkg)) {
                        // balra: összecsukás -> haptic
                        hapticClick()
                        expandedPkgs = expandedPkgs - item.pkg
                        return true
                    }
                }
            }

            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                val item = visibleItems.getOrNull(selectedIndex)
                if (item is VisibleItem.Header) {
                    if (!expandedPkgs.contains(item.pkg)) {
                        // jobbra: kinyitás -> haptic
                        hapticClick()
                        expandedPkgs = expandedPkgs + item.pkg
                        return true
                    }
                }
            }
        }

        return false
    }
}

@Composable
fun rememberWidgetPickerState(
    pm: PackageManager,
    appWidgetManager: AppWidgetManager,
    cellWidthDp: Dp,
    cellHeightDp: Dp,
    onPick: (provider: AppWidgetProviderInfo, spanX: Int, spanY: Int) -> Unit,
    onBack: () -> Unit,
    vibrationEnabled: Boolean = true
): WidgetPickerState {
    val context = LocalContext.current

    val hapticClick = remember(vibrationEnabled, context) {
        { if (vibrationEnabled) Haptics.click(context) }
    }

    return remember(pm, appWidgetManager, cellWidthDp, cellHeightDp, onPick, onBack, hapticClick) {
        WidgetPickerState(
            pm = pm,
            appWidgetManager = appWidgetManager,
            cellWidthDp = cellWidthDp,
            cellHeightDp = cellHeightDp,
            onPick = onPick,
            onBack = onBack,
            hapticClick = hapticClick
        )
    }
}

@Composable
fun WidgetPickerScreen(
    state: WidgetPickerState,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)

    LaunchedEffect(state.selectedIndex, state.visibleItems.size) {
        if (state.visibleItems.isNotEmpty()) {
            listState.animateScrollToItem(state.selectedIndex.coerceIn(0, state.visibleItems.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background)
            .onPreviewKeyEvent { state.handleKey(it) }
            .padding(16.dp)
    ) {
        // ---- HEADER: cím + jobb felső "Vissza" gomb ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Widgets",
                style = MaterialTheme.typography.titleLarge,
                color = cs.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            val interaction = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceVariant.copy(alpha = 0.6f))
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { state.back() } // ✅ haptic benne van
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = cs.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SearchBarLike(
            value = state.query,
            onValueChange = state::updateQuery,
            placeholder = "Search",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(state.visibleItems) { index, item ->
                    val selected = index == state.selectedIndex
                    when (item) {
                        is VisibleItem.Header -> {
                            AppHeaderRow(
                                pm = state.pm,
                                pkg = item.pkg,
                                count = item.count,
                                expanded = state.expandedPkgs.contains(item.pkg),
                                selected = selected,
                                onClick = {
                                    state.selectIndex(index)
                                    state.activateAt(index) // ✅ haptic header toggle
                                }
                            )
                        }

                        is VisibleItem.Widget -> {
                            WidgetRow(
                                pm = state.pm,
                                provider = item.provider,
                                spanX = item.spanX,
                                spanY = item.spanY,
                                selected = selected,
                                onClick = {
                                    state.selectIndex(index)
                                    state.activateAt(index) // ✅ haptic pick
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarLike(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Text("🔍") },
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = cs.surface,
            unfocusedContainerColor = cs.surface
        ),
        modifier = modifier
    )
}

@Composable
private fun AppHeaderRow(
    pm: PackageManager,
    pkg: String,
    count: Int,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }

    val appLabel = remember(pkg) { safeAppLabel(pm, pkg) }
    val iconBmp = remember(pkg) { safeAppIconBitmap(pm, pkg) }

    val bg = if (selected) cs.primary.copy(alpha = 0.10f) else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(bg, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(cs.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text("□", color = cs.onSurfaceVariant)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = appLabel,
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$count widget",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = 0.75f)
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = if (expanded) "▴" else "▾",
            color = cs.onSurface.copy(alpha = 0.75f),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun WidgetRow(
    pm: PackageManager,
    provider: AppWidgetProviderInfo,
    spanX: Int,
    spanY: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }

    val label = remember(provider.provider) { safeWidgetLabel(pm, provider) }
    val sizeText = remember(spanX, spanY) { "${spanX}×${spanY}" }

    val pkg = provider.provider.packageName
    val iconBmp = remember(pkg) { safeAppIconBitmap(pm, pkg) }

    val bg = if (selected) cs.primary.copy(alpha = 0.10f) else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 22.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(bg, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(cs.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Text("▦", color = cs.onSurfaceVariant)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}

private fun inferSpan(
    info: AppWidgetProviderInfo,
    cellW: Float,
    cellH: Float
): Pair<Int, Int> {
    val w = (if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth).toFloat()
    val h = (if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight).toFloat()

    val cw = max(1f, cellW)
    val ch = max(1f, cellH)

    val sx = ceil(w / cw).toInt().coerceIn(1, 6)
    val sy = ceil(h / ch).toInt().coerceIn(1, 6)

    return min(sx, 2) to min(sy, 2)
}

private fun safeAppLabel(pm: PackageManager, pkg: String): String {
    return runCatching {
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai)?.toString()?.trim().orEmpty().ifBlank { pkg }
    }.getOrDefault(pkg)
}

private fun safeWidgetLabel(pm: PackageManager, info: AppWidgetProviderInfo): String {
    return runCatching {
        info.loadLabel(pm)?.toString()?.trim().orEmpty()
    }.getOrDefault("").ifBlank {
        runCatching { info.provider.className.substringAfterLast('.') }.getOrDefault("Widget")
    }
}

private fun safeAppIconBitmap(pm: PackageManager, pkg: String) =
    runCatching { pm.getApplicationIcon(pkg).toBitmap(width = 96, height = 96) }.getOrNull()