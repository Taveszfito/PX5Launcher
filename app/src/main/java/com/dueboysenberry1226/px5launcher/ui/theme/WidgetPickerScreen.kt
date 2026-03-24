@file:OptIn(ExperimentalFoundationApi::class)
@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")

package com.dueboysenberry1226.px5launcher.ui.theme

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
    appWidgetManager: AppWidgetManager,
    private val cellWidthDp: Dp,
    private val cellHeightDp: Dp,
    private val cellGapXDp: Dp,
    private val cellGapYDp: Dp,
    private val maxSpanX: Int,
    private val maxSpanY: Int,
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

    var selectedIndex by mutableIntStateOf(0)
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
                    else (sx <= maxSpanX && sy <= maxSpanY)
                }

                add(VisibleItem.Header(pkg = pkg, count = eligible.size))

                if (expandedPkgs.contains(pkg)) {
                    eligible
                        .sortedBy { (p, _, _) -> safeWidgetLabel(pm, p, widgetFallbackLabel).lowercase() }
                        .forEach { (p, sx, sy) ->
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
        hapticClick()
        expandedPkgs = if (expandedPkgs.contains(pkg)) expandedPkgs - pkg else expandedPkgs + pkg
    }

    fun selectIndex(i: Int) {
        selectedIndex = i.coerceIn(0, max(0, visibleItems.lastIndex))
    }

    fun back() {
        hapticClick()
        onBack()
    }

    fun activateAt(index: Int) {
        val item = visibleItems.getOrNull(index) ?: return
        when (item) {
            is VisibleItem.Header -> toggleExpanded(item.pkg)
            is VisibleItem.Widget -> {
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
    modifier: Modifier = Modifier,
    embeddedInPhonePopup: Boolean = false
) {
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (embeddedInPhonePopup) 24.dp else 18.dp)

    LaunchedEffect(state.selectedIndex, state.visibleItems.size) {
        if (state.visibleItems.isNotEmpty()) {
            listState.animateScrollToItem(state.selectedIndex.coerceIn(0, state.visibleItems.lastIndex))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (embeddedInPhonePopup) Color.Transparent else cs.background)
            .onPreviewKeyEvent { state.handleKey(it) }
            .padding(if (embeddedInPhonePopup) 14.dp else 16.dp)
    ) {
        if (!embeddedInPhonePopup) {
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
                            onClick = { state.back() }
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
        }

        SearchBarLike(
            value = state.query,
            onValueChange = state::updateQuery,
            placeholder = stringResource(R.string.common_search),
            modifier = Modifier.fillMaxWidth(),
            embeddedInPhonePopup = embeddedInPhonePopup
        )

        Spacer(Modifier.height(12.dp))

        Card(
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = if (embeddedInPhonePopup) {
                    Color.White.copy(alpha = 0.035f)
                } else {
                    cs.surface
                }
            ),
            border = BorderStroke(
                1.dp,
                if (embeddedInPhonePopup) {
                    Color.White.copy(alpha = 0.14f)
                } else {
                    cs.outline.copy(alpha = 0.35f)
                }
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 10.dp),
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
                                embeddedInPhonePopup = embeddedInPhonePopup,
                                onClick = {
                                    state.selectIndex(index)
                                    state.activateAt(index)
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
                                embeddedInPhonePopup = embeddedInPhonePopup,
                                onClick = {
                                    state.selectIndex(index)
                                    state.activateAt(index)
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
    modifier: Modifier = Modifier,
    embeddedInPhonePopup: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                color = if (embeddedInPhonePopup) {
                    Color.White.copy(alpha = 0.58f)
                } else {
                    cs.onSurface.copy(alpha = 0.58f)
                }
            )
        },
        leadingIcon = {
            Text(
                text = "🔍",
                fontSize = if (embeddedInPhonePopup) 16.sp else 16.sp
            )
        },
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = if (embeddedInPhonePopup) Color.White else cs.onSurface,
            unfocusedTextColor = if (embeddedInPhonePopup) Color.White else cs.onSurface,
            focusedBorderColor = if (embeddedInPhonePopup) {
                Color.White.copy(alpha = 0.22f)
            } else {
                cs.outline
            },
            unfocusedBorderColor = if (embeddedInPhonePopup) {
                Color.White.copy(alpha = 0.16f)
            } else {
                cs.outline.copy(alpha = 0.7f)
            },
            focusedContainerColor = if (embeddedInPhonePopup) {
                Color.White.copy(alpha = 0.035f)
            } else {
                cs.surface
            },
            unfocusedContainerColor = if (embeddedInPhonePopup) {
                Color.White.copy(alpha = 0.035f)
            } else {
                cs.surface
            },
            cursorColor = if (embeddedInPhonePopup) Color.White else cs.primary
        ),
        modifier = modifier.height(if (embeddedInPhonePopup) 54.dp else 56.dp)
    )
}

@Composable
private fun AppHeaderRow(
    pm: PackageManager,
    pkg: String,
    count: Int,
    expanded: Boolean,
    selected: Boolean,
    embeddedInPhonePopup: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (embeddedInPhonePopup) 18.dp else 14.dp)
    val interaction = remember { MutableInteractionSource() }

    val appLabel = remember(pkg) { safeAppLabel(pm, pkg) }
    val iconBmp = remember(pkg) { safeAppIconBitmap(pm, pkg) }

    val bg = when {
        embeddedInPhonePopup && selected -> Color.White.copy(alpha = 0.10f)
        embeddedInPhonePopup -> Color.White.copy(alpha = 0.03f)
        selected -> cs.primary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = if (embeddedInPhonePopup) 10.dp else 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(bg, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = if (embeddedInPhonePopup) 10.dp else 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (embeddedInPhonePopup) 42.dp else 40.dp)
                .background(
                    if (embeddedInPhonePopup) {
                        Color.White.copy(alpha = 0.08f)
                    } else {
                        cs.surfaceVariant
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(if (embeddedInPhonePopup) 22.dp else 24.dp)
                )
            } else {
                Text(
                    text = "□",
                    color = if (embeddedInPhonePopup) {
                        Color.White.copy(alpha = 0.75f)
                    } else {
                        cs.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = appLabel,
                color = if (embeddedInPhonePopup) Color.White.copy(alpha = 0.96f) else cs.onSurface,
                fontSize = if (embeddedInPhonePopup) 15.sp else MaterialTheme.typography.titleMedium.fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.widget_count, count),
                color = if (embeddedInPhonePopup) {
                    Color.White.copy(alpha = 0.64f)
                } else {
                    cs.onSurface.copy(alpha = 0.75f)
                },
                fontSize = if (embeddedInPhonePopup) 12.sp else MaterialTheme.typography.bodySmall.fontSize
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = if (expanded) "▴" else "▾",
            color = if (embeddedInPhonePopup) {
                Color.White.copy(alpha = 0.74f)
            } else {
                cs.onSurface.copy(alpha = 0.75f)
            },
            fontSize = if (embeddedInPhonePopup) 16.sp else MaterialTheme.typography.titleMedium.fontSize
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
    embeddedInPhonePopup: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (embeddedInPhonePopup) 16.dp else 14.dp)
    val interaction = remember { MutableInteractionSource() }

    val fallback = stringResource(R.string.common_widget)

    val label = remember(provider.provider, fallback) {
        safeWidgetLabel(pm, provider, fallback)
    }

    val sizeText = remember(spanX, spanY) { "${spanX}×${spanY}" }

    val pkg = provider.provider.packageName
    val iconBmp = remember(pkg) { safeAppIconBitmap(pm, pkg) }

    val bg = when {
        embeddedInPhonePopup && selected -> Color.White.copy(alpha = 0.08f)
        embeddedInPhonePopup -> Color.White.copy(alpha = 0.025f)
        selected -> cs.primary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = if (embeddedInPhonePopup) 12.dp else 22.dp, vertical = 3.dp)
            .fillMaxWidth()
            .background(bg, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = if (embeddedInPhonePopup) 9.dp else 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (embeddedInPhonePopup) 40.dp else 42.dp)
                .background(
                    if (embeddedInPhonePopup) {
                        Color.White.copy(alpha = 0.07f)
                    } else {
                        cs.surfaceVariant
                    },
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(if (embeddedInPhonePopup) 20.dp else 26.dp)
                )
            } else {
                Text(
                    text = "▦",
                    color = if (embeddedInPhonePopup) {
                        Color.White.copy(alpha = 0.72f)
                    } else {
                        cs.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (embeddedInPhonePopup) Color.White.copy(alpha = 0.95f) else cs.onSurface,
                fontSize = if (embeddedInPhonePopup) 14.sp else MaterialTheme.typography.titleSmall.fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sizeText,
                color = if (embeddedInPhonePopup) {
                    Color.White.copy(alpha = 0.60f)
                } else {
                    cs.onSurface.copy(alpha = 0.75f)
                },
                fontSize = if (embeddedInPhonePopup) 11.sp else MaterialTheme.typography.bodySmall.fontSize
            )
        }
    }
}

/**
 * span becslés a provider minWidth/minHeight alapján, a cella dp-hez képest.
 * NEM vágjuk le 2×2-re, hanem clamp maxSpanX/maxSpanY-ig.
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
        pm.getApplicationLabel(ai).toString().trim().ifBlank { pkg }
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