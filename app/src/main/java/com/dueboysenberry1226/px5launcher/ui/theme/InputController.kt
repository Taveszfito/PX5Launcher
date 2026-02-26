package com.dueboysenberry1226.px5launcher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent

object InputController {

    enum class Zone { TOPBAR, CONTENT }

    data class State(
        val zone: Zone
    )

    private val OK_CODES = setOf(
        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
        AndroidKeyEvent.KEYCODE_ENTER,
        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
        AndroidKeyEvent.KEYCODE_BUTTON_A
    )

    // ✅ LB / RB támogatás
    val LB_CODES = setOf(
        AndroidKeyEvent.KEYCODE_BUTTON_L1,
        AndroidKeyEvent.KEYCODE_1          // emulator: 1 = LB
    )

    val RB_CODES = setOf(
        AndroidKeyEvent.KEYCODE_BUTTON_R1,
        AndroidKeyEvent.KEYCODE_2          // emulator: 2 = RB
    )

    fun isLB(e: KeyEvent): Boolean {
        val nk = e.nativeKeyEvent
        return nk.action == AndroidKeyEvent.ACTION_DOWN &&
                nk.keyCode in LB_CODES
    }

    fun isRB(e: KeyEvent): Boolean {
        val nk = e.nativeKeyEvent
        return nk.action == AndroidKeyEvent.ACTION_DOWN &&
                nk.keyCode in RB_CODES
    }

    fun route(
        e: KeyEvent,
        state: State,
        topBarHandler: (KeyEvent) -> Boolean,
        contentHandler: (KeyEvent) -> Boolean
    ): Boolean {
        return when (state.zone) {
            Zone.TOPBAR -> topBarHandler(e)
            Zone.CONTENT -> contentHandler(e)
        }
    }
}