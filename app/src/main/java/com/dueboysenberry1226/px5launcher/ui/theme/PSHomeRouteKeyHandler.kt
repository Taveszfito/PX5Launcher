@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.KeyEvent
import com.dueboysenberry1226.px5launcher.data.LaunchableApp
import com.dueboysenberry1226.px5launcher.data.Tab
import com.dueboysenberry1226.px5launcher.data.TopItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun buildPSHomeKeyHandler(
    context: Context,
    scope: CoroutineScope,
    vibrationEnabled: Boolean,
    tabOrder: List<Tab>,
    okCodes: Set<Int>,
    homeSection: HomeSection,
    tab: Tab,
    topBarIndex: Int,
    allAppsOpen: Boolean,
    allApps: List<LaunchableApp>,
    allAppsLastIndex: Int,
    allAppsSelectedIndex: Int,
    columns: Int,
    notifUpToTopbarAllowed: Boolean,
    selectedTopItem: TopItem?,
    selectedApp: LaunchableApp?,
    pinned: Set<String>,
    actionIndex: Int,
    bottomPanel: BottomPanel,
    notifEnterFocusTick: Int,
    notifKeyHandler: ((KeyEvent) -> Boolean)?,
    widgetsKeyHandler: ((KeyEvent) -> Boolean)?,
    calendarKeyHandler: ((KeyEvent) -> Boolean)?,
    musicKeyHandler: ((KeyEvent) -> Boolean)?,
    onOpenSettings: () -> Unit,
    focusManager: FocusManager,
    moveBottomPanelHorizontal: (Int) -> Boolean,
    stepStripByController: (Int) -> Unit,
    goActions: () -> Unit,
    goWidgets: () -> Unit,
    goHomeTopKeepScroll: () -> Unit,
    focusActions: () -> Unit,
    setTab: (Tab) -> Unit,
    setTopBarIndex: (Int) -> Unit,
    setHomeSection: (HomeSection) -> Unit,
    setAllAppsOpen: (Boolean) -> Unit,
    setAllAppsSelectedIndex: (Int) -> Unit,
    setActionIndex: (Int) -> Unit,
    setSearchOpen: (Boolean) -> Unit,
    setSearchQuery: (String) -> Unit,
    setNotifEnterFocusTick: (Int) -> Unit,
    launchApp: (LaunchableApp) -> Unit,
    togglePin: (LaunchableApp) -> Unit,
    openMenuFor: (LaunchableApp?) -> Unit,
    closeMenu: () -> Unit
): (KeyEvent) -> Boolean = { e ->
    val nk = e.nativeKeyEvent
    val code = nk.keyCode
    val action = nk.action

    val isLB = code == AndroidKeyEvent.KEYCODE_BUTTON_L1 || code == AndroidKeyEvent.KEYCODE_1
    val isRB = code == AndroidKeyEvent.KEYCODE_BUTTON_R1 || code == AndroidKeyEvent.KEYCODE_2

    if (homeSection == HomeSection.TOPBAR && code in okCodes) {
        if (action == AndroidKeyEvent.ACTION_DOWN || action == AndroidKeyEvent.ACTION_UP) {
            setTab(
                when (topBarIndex) {
                    0 -> Tab.GAMES
                    1 -> Tab.MEDIA
                    else -> Tab.NOTIFICATIONS
                }
            )
            true
        } else {
            false
        }
    } else if (action != AndroidKeyEvent.ACTION_DOWN) {
        false
    } else {
        if (vibrationEnabled) {
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_BUTTON_A,
                AndroidKeyEvent.KEYCODE_BUTTON_B,
                AndroidKeyEvent.KEYCODE_BACK -> Haptics.click(context)
            }
        }

        if (!allAppsOpen && (isLB || isRB)) {
            if (homeSection == HomeSection.WIDGETS) {
                moveBottomPanelHorizontal(if (isLB) -1 else +1)
                true
            } else {
                val idx = tabOrder.indexOf(tab).takeIf { it >= 0 } ?: 0
                val nextIdx = ((idx + (if (isLB) -1 else +1)) % tabOrder.size + tabOrder.size) % tabOrder.size
                val nextTab = tabOrder[nextIdx]
                if (tab != nextTab) setTab(nextTab)
                true
            }
        } else if (homeSection == HomeSection.TOPBAR) {
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (allAppsOpen) {
                        setAllAppsSelectedIndex(0)
                        setHomeSection(HomeSection.TOP)
                        focusManager.clearFocus(force = true)
                        true
                    } else {
                        when (tab) {
                            Tab.GAMES, Tab.MEDIA -> {
                                setHomeSection(HomeSection.TOP)
                                true
                            }
                            Tab.NOTIFICATIONS -> {
                                setHomeSection(HomeSection.NOTIFS)
                                setNotifEnterFocusTick(notifEnterFocusTick + 1)
                                focusManager.clearFocus(force = true)
                                true
                            }
                        }
                    }
                }

                AndroidKeyEvent.KEYCODE_DPAD_UP -> true

                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    val next = (topBarIndex - 1).coerceAtLeast(0)
                    setTopBarIndex(next)
                    if (next <= 2) {
                        setTab(when (next) {
                            0 -> Tab.GAMES
                            1 -> Tab.MEDIA
                            else -> Tab.NOTIFICATIONS
                        })
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val next = (topBarIndex + 1).coerceAtMost(4)
                    setTopBarIndex(next)
                    if (next <= 2) {
                        setTab(when (next) {
                            0 -> Tab.GAMES
                            1 -> Tab.MEDIA
                            else -> Tab.NOTIFICATIONS
                        })
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                    when (topBarIndex) {
                        0 -> setTab(Tab.GAMES)
                        1 -> setTab(Tab.MEDIA)
                        2 -> setTab(Tab.NOTIFICATIONS)
                        3 -> setSearchOpen(true)
                        4 -> onOpenSettings()
                    }
                    true
                }

                AndroidKeyEvent.KEYCODE_BACK -> true
                else -> false
            }
        } else if (allAppsOpen) {
            val lastApp = (allApps.size - 1).coerceAtLeast(0)
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    when {
                        allAppsSelectedIndex == -1 -> true
                        allAppsSelectedIndex % columns == 0 -> {
                            setAllAppsSelectedIndex(-1)
                            true
                        }
                        allAppsSelectedIndex > 0 -> {
                            setAllAppsSelectedIndex(allAppsSelectedIndex - 1)
                            true
                        }
                        else -> true
                    }
                }

                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    when {
                        allAppsSelectedIndex == -1 -> {
                            setAllAppsSelectedIndex(0)
                            true
                        }
                        allAppsSelectedIndex < lastApp -> {
                            setAllAppsSelectedIndex(allAppsSelectedIndex + 1)
                            true
                        }
                        else -> true
                    }
                }

                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    when {
                        allAppsSelectedIndex == -1 -> {
                            setHomeSection(HomeSection.TOPBAR)
                            setTopBarIndex(when (tab) {
                                Tab.GAMES -> 0
                                Tab.MEDIA -> 1
                                Tab.NOTIFICATIONS -> 2
                            })
                            focusManager.clearFocus(force = true)
                            true
                        }

                        allAppsSelectedIndex < columns -> {
                            setHomeSection(HomeSection.TOPBAR)
                            setTopBarIndex(when (tab) {
                                Tab.GAMES -> 0
                                Tab.MEDIA -> 1
                                Tab.NOTIFICATIONS -> 2
                            })
                            setAllAppsSelectedIndex(-2)
                            focusManager.clearFocus(force = true)
                            true
                        }

                        else -> {
                            setAllAppsSelectedIndex((allAppsSelectedIndex - columns).coerceAtLeast(0))
                            true
                        }
                    }
                }

                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    when (allAppsSelectedIndex) {
                        -2 -> true
                        -1 -> {
                            setAllAppsSelectedIndex(0)
                            true
                        }
                        else -> {
                            val next = allAppsSelectedIndex + columns
                            if (next <= lastApp) setAllAppsSelectedIndex(next)
                            true
                        }
                    }
                }

                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                    when (allAppsSelectedIndex) {
                        -1 -> {
                            setAllAppsOpen(false)
                            true
                        }
                        -2 -> true
                        else -> {
                            val app = allApps.getOrNull(allAppsSelectedIndex)
                            if (app != null) {
                                setAllAppsOpen(false)
                                launchApp(app)
                            }
                            true
                        }
                    }
                }

                AndroidKeyEvent.KEYCODE_BUTTON_B,
                AndroidKeyEvent.KEYCODE_BACK -> {
                    setAllAppsOpen(false)
                    true
                }

                else -> false
            }
        } else {
            when (homeSection) {
                HomeSection.NOTIFS -> {
                    val handledByNotif = notifKeyHandler?.invoke(e) == true
                    if (handledByNotif) {
                        true
                    } else {
                        when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                if (notifUpToTopbarAllowed) {
                                    setHomeSection(HomeSection.TOPBAR)
                                    setTopBarIndex(when (tab) {
                                        Tab.GAMES -> 0
                                        Tab.MEDIA -> 1
                                        Tab.NOTIFICATIONS -> 2
                                    })
                                    focusManager.clearFocus(force = true)
                                    true
                                } else false
                            }

                            AndroidKeyEvent.KEYCODE_BACK -> {
                                setHomeSection(HomeSection.TOPBAR)
                                focusManager.clearFocus(force = true)
                                true
                            }

                            else -> false
                        }
                    }
                }

                HomeSection.TOP -> {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            stepStripByController(-1)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            stepStripByController(+1)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            setHomeSection(HomeSection.TOPBAR)
                            setTopBarIndex(when (tab) {
                                Tab.GAMES -> 0
                                Tab.MEDIA -> 1
                                Tab.NOTIFICATIONS -> 2
                            })
                            focusManager.clearFocus(force = true)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            setHomeSection(HomeSection.ACTIONS)
                            setActionIndex(0)
                            goActions()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                            when (selectedTopItem) {
                                is TopItem.App -> launchApp(selectedTopItem.app)
                                is TopItem.AllApps -> {
                                    setAllAppsOpen(true)
                                    setAllAppsSelectedIndex(allAppsSelectedIndex.coerceIn(0, allAppsLastIndex.coerceAtLeast(0)))
                                }
                                else -> Unit
                            }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_BUTTON_Y -> {
                            setSearchOpen(true)
                            true
                        }
                        AndroidKeyEvent.KEYCODE_BUTTON_X -> {
                            if (selectedApp != null) {
                                togglePin(selectedApp)
                                true
                            } else false
                        }
                        AndroidKeyEvent.KEYCODE_BACK -> {
                            when {
                                else -> {
                                    closeMenu()
                                    setSearchOpen(false)
                                    setSearchQuery("")
                                    true
                                }
                            }
                        }
                        else -> false
                    }
                }

                HomeSection.ACTIONS -> {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            focusActions()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            focusActions()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            setHomeSection(HomeSection.TOP)
                            focusManager.clearFocus(force = true)
                            goHomeTopKeepScroll()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            setHomeSection(HomeSection.WIDGETS)
                            goWidgets()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                        AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                            val canLaunchNow = selectedTopItem is TopItem.App
                            if (actionIndex == 0) {
                                if (canLaunchNow) launchApp((selectedTopItem as TopItem.App).app)
                            } else {
                                if (canLaunchNow) openMenuFor((selectedTopItem as TopItem.App).app)
                            }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_BACK -> {
                            closeMenu()
                            setSearchOpen(false)
                            setSearchQuery("")
                            true
                        }
                        else -> false
                    }
                }

                HomeSection.WIDGETS -> {
                    val handledByPanel = when (bottomPanel) {
                        BottomPanel.WIDGETS -> widgetsKeyHandler?.invoke(e) == true
                        BottomPanel.CALENDAR -> calendarKeyHandler?.invoke(e) == true
                        BottomPanel.MUSIC -> musicKeyHandler?.invoke(e) == true
                    }

                    if (handledByPanel) {
                        true
                    } else {
                        when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> moveBottomPanelHorizontal(-1)
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> moveBottomPanelHorizontal(+1)
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                setHomeSection(HomeSection.ACTIONS)
                                goActions()
                                true
                            }
                            AndroidKeyEvent.KEYCODE_BACK,
                            AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                                setHomeSection(HomeSection.ACTIONS)
                                goActions()
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                            else -> false
                        }
                    }
                }

                HomeSection.TOPBAR -> false
            }
        }
    }
}
