@file:OptIn(ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")

package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.LaunchableApp
import com.dueboysenberry1226.px5launcher.data.TopItem
import com.dueboysenberry1226.px5launcher.data.WidgetGridSpec
import com.dueboysenberry1226.px5launcher.data.WidgetLayoutMode
import com.dueboysenberry1226.px5launcher.data.WidgetPlacement

@Composable
internal fun PSHomeGamesBody(
    topData: PSHomeDerivedData,
    homeSection: HomeSection,
    allAppsOpen: Boolean,
    allAppsSelectedIndex: Int,
    topIndex: Int,
    showBigAppName: Boolean,
    vibrationEnabled: Boolean,
    actionIndex: Int,
    bottomPanel: BottomPanel,
    playFR: FocusRequester,
    menuFR: FocusRequester,
    widgetsFR: FocusRequester,
    calendarFR: FocusRequester,
    musicFR: FocusRequester,
    widgetHost: AppWidgetHost,
    widgetManager: AppWidgetManager,
    gridSpec: WidgetGridSpec,
    placements: List<WidgetPlacement>,
    onSetAllAppsSelectedIndex: (Int) -> Unit,
    onSetAllAppsOpen: (Boolean) -> Unit,
    onSetTopIndex: (Int) -> Unit,
    onSetHomeSection: (HomeSection) -> Unit,
    onSetWidgetsKeyHandler: (((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?) -> Unit,
    onSetCalendarKeyHandler: (((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?) -> Unit,
    onSetMusicKeyHandler: (((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?) -> Unit,
    onSetCellPx: (Float) -> Unit,
    onRequestAddAt: (Int, Int) -> Unit,
    onMoveWidgetClockwise: (WidgetPlacement) -> Unit,
    onDeleteWidget: (WidgetPlacement) -> Unit,
    onGoHomeTopKeepScroll: () -> Unit,
    onOpenMenuFor: (LaunchableApp?) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onGoActions: () -> Unit,
    currentPanelFR: () -> FocusRequester
) {
    val context = LocalContext.current
    val rawSelectedTopItem = topData.topItems.getOrNull(topIndex + topData.anchorIndex)
    val selectedTopItem = topData.selectedTopItem(topIndex, homeSection)
    val uiHidesSelectedApp = homeSection == HomeSection.TOPBAR || homeSection == HomeSection.WIDGETS

    if (allAppsOpen) {
        AllAppsScreen(
            items = topData.allAppsGrid,
            selectedIndex = allAppsSelectedIndex,
            columns = topData.columns,
            onSelectChange = onSetAllAppsSelectedIndex,
            onLaunch = { a ->
                onSetAllAppsOpen(false)
                onLaunchApp(a)
            },
            onBack = { onSetAllAppsOpen(false) },
            onLongPress = { a ->
                onSetAllAppsSelectedIndex(
                    topData.allAppsGrid.indexOfFirst {
                        it is AllAppsGridItem.App && it.app.packageName == a.packageName
                    }.coerceAtLeast(0)
                )
                onOpenMenuFor(a)
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        state = topData.homeScrollState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            HomeTopStrip(
                items = topData.topItems,
                displayIndex = topIndex + topData.anchorIndex,
                pinned = topData.pinned,
                stripState = topData.stripState,
                fling = topData.fling,
                showSelection = !uiHidesSelectedApp,
                modifier = Modifier.fillMaxWidth(),
                onSelectIndex = { idx ->
                    val target = idx - topData.anchorIndex
                    if (target in 0..topData.realLastIndex) onSetTopIndex(target)
                },
                onActivate = { item ->
                    if (homeSection == HomeSection.TOPBAR) {
                        onSetHomeSection(HomeSection.TOP)
                        onGoHomeTopKeepScroll()
                        when (item) {
                            is TopItem.App -> {
                                val target = topData.realItems.indexOfFirst {
                                    it is TopItem.App && it.app.packageName == item.app.packageName
                                }
                                if (target >= 0) onSetTopIndex(target)
                            }
                            is TopItem.AllApps -> {
                                val target = topData.realItems.indexOfFirst { it is TopItem.AllApps }
                                if (target >= 0) onSetTopIndex(target)
                            }
                            else -> Unit
                        }
                    } else {
                        when (item) {
                            is TopItem.App -> {
                                val curPkg = (rawSelectedTopItem as? TopItem.App)?.app?.packageName
                                if (curPkg == item.app.packageName) onLaunchApp(item.app)
                                else {
                                    val target = topData.realItems.indexOfFirst {
                                        it is TopItem.App && it.app.packageName == item.app.packageName
                                    }
                                    if (target >= 0) onSetTopIndex(target)
                                }
                            }
                            is TopItem.AllApps -> {
                                val isAlready = rawSelectedTopItem is TopItem.AllApps
                                if (isAlready) onSetAllAppsOpen(true)
                                else {
                                    val target = topData.realItems.indexOfFirst { it is TopItem.AllApps }
                                    if (target >= 0) onSetTopIndex(target)
                                }
                            }
                            else -> Unit
                        }
                    }
                },
                onLongPress = { item -> if (item is TopItem.App) onOpenMenuFor(item.app) },
                onSelectionTick = { if (vibrationEnabled) Haptics.tick(context) }
            )

            Spacer(Modifier.height(18.dp))
            Spacer(Modifier.height(18.dp))

            if (showBigAppName) {
                Text(
                    text = when (selectedTopItem) {
                        is TopItem.App -> selectedTopItem.app.label
                        is TopItem.AllApps -> context.getString(R.string.homescreen_apps_title)
                        else -> ""
                    },
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 44.sp
                )
                Spacer(Modifier.height(14.dp))
            } else {
                Spacer(Modifier.height(6.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HomeActions(
                    canLaunch = selectedTopItem is TopItem.App,
                    playFR = playFR,
                    menuFR = menuFR,
                    onPlay = {
                        if (vibrationEnabled) Haptics.click(context)
                        (selectedTopItem as? TopItem.App)?.app?.let(onLaunchApp)
                    },
                    onMenu = {
                        if (vibrationEnabled) Haptics.click(context)
                        if (selectedTopItem is TopItem.App) onOpenMenuFor(selectedTopItem.app)
                    }
                )

                Spacer(Modifier.width(18.dp))

                AnimatedVisibility(
                    visible = homeSection == HomeSection.WIDGETS,
                    modifier = Modifier.weight(1f),
                    enter = fadeIn(animationSpec = tween(180)),
                    exit = fadeOut(animationSpec = tween(180))
                ) {
                    BottomPanelTabsRow(active = bottomPanel)
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        item {
            val panelFR = currentPanelFR()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth().height(220.dp).focusRequester(panelFR).focusable()
            ) {
                AnimatedContent(
                    targetState = bottomPanel,
                    transitionSpec = {
                        val order = listOf(BottomPanel.WIDGETS, BottomPanel.CALENDAR, BottomPanel.MUSIC)
                        val from = order.indexOf(initialState).coerceAtLeast(0)
                        val to = order.indexOf(targetState).coerceAtLeast(0)
                        val dir = if (to > from) 1 else -1
                        (slideInHorizontally(animationSpec = tween(220), initialOffsetX = { full -> full * dir }) + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(animationSpec = tween(220), targetOffsetX = { full -> -full * dir }) + fadeOut(tween(180))
                            )
                    },
                    label = "bottomPanel"
                ) { p ->
                    when (p) {
                        BottomPanel.WIDGETS -> {
                            WidgetsPanel(
                                placements = placements,
                                grid = gridSpec,
                                layoutMode = WidgetLayoutMode.LANDSCAPE,
                                focusRequester = widgetsFR,
                                registerKeyHandler = { onSetWidgetsKeyHandler(it) },
                                onRequestAddAt = onRequestAddAt,
                                onMove = onMoveWidgetClockwise,
                                onDelete = onDeleteWidget,
                                onCellPxKnown = onSetCellPx,
                                renderWidget = { placement, modifier ->
                                    key(placement.appWidgetId, placement.provider, placement.cellX, placement.cellY) {
                                        val info = widgetManager.getAppWidgetInfo(placement.appWidgetId)
                                        if (info == null) {
                                            Box(modifier, contentAlignment = Alignment.Center) {
                                                Text(
                                                    context.getString(R.string.homescreen_widget_error_title),
                                                    color = Color.White.copy(alpha = 0.65f)
                                                )
                                            }
                                        } else {
                                            AndroidView(
                                                factory = { ctx ->
                                                    try {
                                                        widgetHost.createView(ctx, placement.appWidgetId, info).apply {
                                                            setAppWidget(placement.appWidgetId, info)
                                                            setPadding(0, 0, 0, 0)
                                                            layoutParams = FrameLayout.LayoutParams(
                                                                FrameLayout.LayoutParams.MATCH_PARENT,
                                                                FrameLayout.LayoutParams.MATCH_PARENT
                                                            )
                                                        }
                                                    } catch (_: Throwable) {
                                                        FrameLayout(ctx).apply {
                                                            addView(TextView(ctx).apply {
                                                                text = context.getString(R.string.homescreen_widget_error_title)
                                                            })
                                                        }
                                                    }
                                                },
                                                update = { view ->
                                                    view.setPadding(0, 0, 0, 0)
                                                    view.layoutParams = FrameLayout.LayoutParams(
                                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                                        FrameLayout.LayoutParams.MATCH_PARENT
                                                    )
                                                },
                                                modifier = modifier
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        BottomPanel.CALENDAR -> {
                            CalendarPanelCard(
                                modifier = Modifier.fillMaxSize(),
                                vibrationEnabled = vibrationEnabled,
                                focusRequester = calendarFR,
                                registerKeyHandler = { onSetCalendarKeyHandler(it) }
                            )
                        }

                        BottomPanel.MUSIC -> {
                            MusicControlPanelCard(
                                modifier = Modifier.fillMaxSize(),
                                registerKeyHandler = { onSetMusicKeyHandler(it) },
                                focusRequester = musicFR
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
        }
    }
}
