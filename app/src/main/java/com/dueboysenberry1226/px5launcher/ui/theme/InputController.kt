package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.ui.input.key.KeyEvent

object InputController {

    enum class Zone { TOPBAR, CONTENT }

    data class State(
        val zone: Zone
    )

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