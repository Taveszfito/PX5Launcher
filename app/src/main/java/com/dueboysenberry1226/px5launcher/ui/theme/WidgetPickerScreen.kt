@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import kotlin.math.ceil
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.ui.Haptics
import kotlin.math.max

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
    private val context: Context,
    val pm: PackageManager,
    private val appWidgetManager: AppWidgetManager,
    private val cellWidthDp: Dp,
    private val cellHeightDp: Dp,
    private val cellGapXDp: Dp,
    private val cellGapYDp: Dp,

    // ✅ új: mennyi a maximum engedett span (külön portrait/landscape)
    private val maxSpanX: Int,
    private val maxSpanY: Int,

    // ✅ új: landscape-ban kiszűrjük a túl nagy widgeteket (portraitban nem)
    private val filterOutOversize: Boolean,

    private val onPick: (AppWidgetProviderInfo, Int, Int) -> Unit,
    private val onBack: () -> Unit,
    private val hapticClick: () -> Unit,
    private val widgetFallbackLabel: String
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
                val wLabel = safeWidgetLabel(pm, p, widgetFallbackLabel).lowercase()
                appLabel.contains(q) || wLabel.contains(q) || pkg.lowercase().contains(q)
            }
            if (kept.isEmpty()) null else pkg to kept
        }
    }

    val visibleItems: List<VisibleItem> by derivedStateOf {
        buildList {
            filtered.forEach { (pkg, list) ->
                // a header count-ot úgy számoljuk, hogy a ténylegesen listázott widgetek száma legyen
                val eligible = list.map { p ->
                    val (sx, sy) = inferSpan(
                        context = context,
                        info = p,
                        cellW = cellWidthDp.value,
                        cellH = cellHeightDp.value,
                        gapX = cellGapXDp.value,
                        gapY = cellGapYDp.value,
                        maxSpanX = maxSpanX,
                        maxSpanY = maxSpanY
                    )
                    Triple(p, sx, sy)
                }.filter { (_, sx, sy) ->
                    if (!filterOutOversize) true
                    else (sx <= maxSpanX && sy <= maxSpanY) // itt igazából mindig igaz, mert clamp-elt, de hagyjuk érthetően
                }

                add(VisibleItem.Header(pkg = pkg, count = eligible.size))

                if (expandedPkgs.contains(pkg)) {
                    eligible
                        .sortedBy { (p, _, _) -> safeWidgetLabel(pm, p, widgetFallbackLabel).lowercase() }
                        .forEach { (p, sx, sy) ->
                            // ✅ ha landscape filterOutOversize = true és valamiért mégis túl nagy lenne,
                            // akkor itt vágjuk ki (biztonsági)
                            if (filterOutOversize && (sx > maxSpanX || sy > maxSpanY)) return@forEach
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
    cellGapXDp: Dp,
    cellGapYDp: Dp,

    // ✅ új paraméterek:
    // - landscape: maxSpanX=2, maxSpanY=2, filterOutOversize=true
    // - portrait : maxSpanX=4, maxSpanY=5, filterOutOversize=false
    maxSpanX: Int,
    maxSpanY: Int,
    filterOutOversize: Boolean,

    onPick: (provider: AppWidgetProviderInfo, spanX: Int, spanY: Int) -> Unit,
    onBack: () -> Unit,
    vibrationEnabled: Boolean = true
): WidgetPickerState {
    val context = LocalContext.current
    val widgetFallbackLabel = stringResource(R.string.common_widget)

    val hapticClick = remember(vibrationEnabled, context) {
        { if (vibrationEnabled) Haptics.click(context) }
    }

    return remember(
        pm,
        appWidgetManager,
        cellWidthDp,
        cellHeightDp,
        cellGapXDp,
        cellGapYDp,
        maxSpanX,
        maxSpanY,
        filterOutOversize,
        onPick,
        onBack,
        hapticClick,
        widgetFallbackLabel
    ) {
        WidgetPickerState(
            context = context,
            pm = pm,
            appWidgetManager = appWidgetManager,
            cellWidthDp = cellWidthDp,
            cellHeightDp = cellHeightDp,
            cellGapXDp = cellGapXDp,
            cellGapYDp = cellGapYDp,
            maxSpanX = maxSpanX.coerceAtLeast(1),
            maxSpanY = maxSpanY.coerceAtLeast(1),
            filterOutOversize = filterOutOversize,
            onPick = onPick,
            onBack = onBack,
            hapticClick = hapticClick,
            widgetFallbackLabel = widgetFallbackLabel
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
        // ---- HEADER: cím + jobb felső X ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.widget_picker_title),
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
                    text = "✕", // ez szimbólum, nem kell fordítani
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
            placeholder = stringResource(R.string.common_search),
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
        leadingIcon = { Text("🔍") }, // ikon, nem fordítandó
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
                Text("□", color = cs.onSurfaceVariant) // ikon, nem fordítandó
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
                text = stringResource(R.string.widget_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = 0.75f)
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = if (expanded) "▴" else "▾", // ikon, nem fordítandó
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

    val fallback = stringResource(R.string.common_widget)

    val label = remember(provider.provider, fallback) {
        safeWidgetLabel(pm, provider, fallback)
    }

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

/**
 * span becslés a provider minWidth/minHeight alapján, a cella dp-hez képest.
 * ✅ NEM vágjuk le 2×2-re, hanem clamp maxSpanX/maxSpanY-ig.
 */
private fun inferSpan(
    context: Context,
    info: AppWidgetProviderInfo,
    cellW: Float,
    cellH: Float,
    gapX: Float,
    gapY: Float,
    maxSpanX: Int,
    maxSpanY: Int
): Pair<Int, Int> {

    val safeMaxX = maxSpanX.coerceAtLeast(1)
    val safeMaxY = maxSpanY.coerceAtLeast(1)

    val density = context.resources.displayMetrics.density

    val defaultPadding = AppWidgetHostView.getDefaultPaddingForWidget(
        context,
        info.provider,
        null
    )

    // px → dp konverzió
    val horizontalPadding =
        (defaultPadding.left + defaultPadding.right) / density

    val verticalPadding =
        (defaultPadding.top + defaultPadding.bottom) / density


    val rawMinWidth = when {
        info.minResizeWidth > 0 -> info.minResizeWidth / density
        info.minWidth > 0 -> info.minWidth / density
        else -> cellW
    }

    val rawMinHeight = when {
        info.minResizeHeight > 0 -> info.minResizeHeight / density
        info.minHeight > 0 -> info.minHeight / density
        else -> cellH
    }

    val contentWidth = (rawMinWidth - horizontalPadding).coerceAtLeast(1f)
    val contentHeight = (rawMinHeight - verticalPadding).coerceAtLeast(1f)

    val cw = cellW.coerceAtLeast(1f)
    val ch = cellH.coerceAtLeast(1f)
    val gx = gapX.coerceAtLeast(0f)
    val gy = gapY.coerceAtLeast(0f)

    fun spanFor(required: Float, cell: Float, gap: Float, maxSpan: Int): Int {
        var span = 1
        while (span < maxSpan) {
            val available = span * cell + (span - 1) * gap
            if (required <= available + 4f) break
            span++
        }
        return span.coerceIn(1, maxSpan)
    }

    val spanX = spanFor(
        required = contentWidth,
        cell = cw,
        gap = gx,
        maxSpan = safeMaxX
    )

    val spanY = spanFor(
        required = contentHeight,
        cell = ch,
        gap = gy,
        maxSpan = safeMaxY
    )

    return spanX to spanY
}

private fun safeAppLabel(pm: PackageManager, pkg: String): String {
    return runCatching {
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai)?.toString()?.trim().orEmpty().ifBlank { pkg }
    }.getOrDefault(pkg)
}

private fun safeWidgetLabel(
    pm: PackageManager,
    info: AppWidgetProviderInfo,
    widgetFallbackLabel: String
): String {
    return runCatching {
        info.loadLabel(pm)?.toString()?.trim().orEmpty()
    }.getOrDefault("").ifBlank {
        runCatching { info.provider.className.substringAfterLast('.') }.getOrDefault(widgetFallbackLabel)
            .ifBlank { widgetFallbackLabel }
    }
}

private fun safeAppIconBitmap(pm: PackageManager, pkg: String) =
    runCatching { pm.getApplicationIcon(pkg).toBitmap(width = 96, height = 96) }.getOrNull()