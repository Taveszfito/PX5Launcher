@file:OptIn(ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")

package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxScope
import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.dueboysenberry1226.px5launcher.data.Tab
import kotlin.math.abs
import kotlin.math.max

@Composable
internal fun PSHomeMainBox(
    context: Context,
    bg: Brush,
    swipeThresholdPx: Float,
    showWidgetPicker: Boolean,
    tab: Tab,
    homeSection: HomeSection,
    bottomPanel: BottomPanel,
    menuOpen: Boolean,
    searchOpen: Boolean,
    allAppsOpen: Boolean,
    vibrationEnabled: Boolean,
    keyHandler: (KeyEvent) -> Boolean,
    handlePickerKey: (KeyEvent) -> Boolean,
    handleMediaKey: (KeyEvent) -> Boolean,
    onCloseWidgetPicker: () -> Unit,
    onBackConsumed: () -> Boolean,
    onStepStripByController: (Int) -> Unit,
    onMoveBottomPanelHorizontal: (Int) -> Unit,
    onGoHomeTopKeepScroll: () -> Unit,
    onGoWidgets: () -> Unit,
    onSwipeToTop: () -> Unit,
    focusManager: FocusManager,
    topBarIndex: Int,
    is24h: Boolean,
    onSetTab: (Tab) -> Unit,
    onSetSearchOpen: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
    overlays: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(allAppsOpen, showWidgetPicker, tab, homeSection, bottomPanel, menuOpen, searchOpen) {
                if (allAppsOpen || showWidgetPicker || menuOpen || searchOpen) return@pointerInput
                if (tab == Tab.MEDIA || tab == Tab.NOTIFICATIONS) return@pointerInput

                val appStepPx = 96.dp.toPx()
                val tapSlopPx = 10.dp.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val id = down.id
                    var totalX = 0f
                    var totalY = 0f
                    var stepAccX = 0f
                    var steppedCount = 0
                    var dragDominant: Boolean? = null

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == id } ?: break
                        if (!change.pressed) break

                        val d = change.positionChange()
                        totalX += d.x
                        totalY += d.y

                        if (abs(totalX) + abs(totalY) > 2f) change.consume()

                        val axNow = abs(totalX)
                        val ayNow = abs(totalY)
                        val maxNow = max(axNow, ayNow)

                        if (dragDominant == null && maxNow >= swipeThresholdPx) {
                            dragDominant = axNow > ayNow
                        }

                        if (dragDominant == true) {
                            if (homeSection != HomeSection.WIDGETS && homeSection != HomeSection.TOPBAR) {
                                stepAccX += d.x
                                while (stepAccX <= -appStepPx) {
                                    stepAccX += appStepPx
                                    if (steppedCount < 12) {
                                        onStepStripByController(+1)
                                        steppedCount++
                                    } else break
                                }
                                while (stepAccX >= appStepPx) {
                                    stepAccX -= appStepPx
                                    if (steppedCount < 12) {
                                        onStepStripByController(-1)
                                        steppedCount++
                                    } else break
                                }
                            }
                        }
                    }

                    val ax = abs(totalX)
                    val ay = abs(totalY)
                    val maxA = max(ax, ay)

                    if (maxA < tapSlopPx) {
                        if (homeSection == HomeSection.TOPBAR) {
                            focusManager.clearFocus(force = true)
                            onGoHomeTopKeepScroll()
                        }
                        return@awaitEachGesture
                    }

                    if (maxA < swipeThresholdPx) return@awaitEachGesture

                    if (homeSection == HomeSection.TOPBAR) {
                        if (ay >= ax) {
                            if (totalY < 0f) onGoWidgets()
                            else {
                                focusManager.clearFocus(force = true)
                                onGoHomeTopKeepScroll()
                            }
                        }
                        return@awaitEachGesture
                    }

                    if (ax > ay) {
                        if (homeSection == HomeSection.WIDGETS) {
                            if (totalX < 0f) onMoveBottomPanelHorizontal(+1) else onMoveBottomPanelHorizontal(-1)
                        }
                    } else {
                        if (totalY < 0f) onGoWidgets() else onSwipeToTop()
                    }
                }
            }
            .onPreviewKeyEvent { e ->
                val nk = e.nativeKeyEvent

                if (showWidgetPicker) {
                    if (nk.action == AndroidKeyEvent.ACTION_DOWN) {
                        when (nk.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A,
                            AndroidKeyEvent.KEYCODE_BACK,
                            AndroidKeyEvent.KEYCODE_ESCAPE -> if (vibrationEnabled) Haptics.click(context)
                        }
                    }

                    val handled = handlePickerKey(e)
                    if (handled) return@onPreviewKeyEvent true

                    if (nk.action == AndroidKeyEvent.ACTION_DOWN &&
                        (nk.keyCode == AndroidKeyEvent.KEYCODE_BACK || nk.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE)
                    ) {
                        onCloseWidgetPicker()
                        return@onPreviewKeyEvent true
                    }

                    return@onPreviewKeyEvent false
                }

                if (nk.action == AndroidKeyEvent.ACTION_DOWN && nk.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                    return@onPreviewKeyEvent onBackConsumed()
                }

                if (tab == Tab.MEDIA && homeSection != HomeSection.TOPBAR) {
                    val handledByMedia = handleMediaKey(e)
                    if (handledByMedia) return@onPreviewKeyEvent true
                    if (nk.action == AndroidKeyEvent.ACTION_DOWN && nk.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP) {
                        return@onPreviewKeyEvent keyHandler(e)
                    }
                    return@onPreviewKeyEvent false
                }

                if (allAppsOpen) {
                    if (nk.action == AndroidKeyEvent.ACTION_DOWN &&
                        (nk.keyCode == AndroidKeyEvent.KEYCODE_BACK || nk.keyCode == AndroidKeyEvent.KEYCODE_BUTTON_B)
                    ) {
                        return@onPreviewKeyEvent keyHandler(e)
                    }
                    return@onPreviewKeyEvent keyHandler(e)
                }

                InputController.route(
                    e = e,
                    state = InputController.State(
                        if (homeSection == HomeSection.TOPBAR) InputController.Zone.TOPBAR
                        else InputController.Zone.CONTENT
                    ),
                    topBarHandler = { ev -> keyHandler(ev) },
                    contentHandler = { keyHandler(it) }
                )
            }
            .background(bg)
            .padding(horizontal = 26.dp, vertical = 18.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            PSHomeTopBarHost(
                tab = tab,
                is24h = is24h,
                onTabChange = onSetTab,
                onSearch = { onSetSearchOpen(true) },
                onSettings = onOpenSettings,
                topBarFocused = homeSection == HomeSection.TOPBAR,
                topBarIndex = topBarIndex
            )

            Spacer(Modifier.height(14.dp))
            body()
        }

        overlays()
    }
}
