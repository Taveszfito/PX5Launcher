package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeActions(
    canLaunch: Boolean,
    playFR: FocusRequester,
    menuFR: FocusRequester,
    onPlay: () -> Unit,
    onMenu: () -> Unit
) {
    var playFocused by remember { mutableStateOf(false) }
    var menuFocused by remember { mutableStateOf(false) }

    Row {
        Button(
            onClick = onPlay,
            enabled = canLaunch,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (playFocused)
                    Color.White.copy(alpha = 0.26f)
                else Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.10f),
                disabledContentColor = Color.White.copy(alpha = 0.35f)
            ),
            border = if (playFocused)
                BorderStroke(2.dp, Color.White.copy(alpha = 0.95f))
            else null,
            modifier = Modifier
                .focusRequester(playFR)
                .onFocusChanged { playFocused = it.isFocused }
                .focusable()
        ) {
            Text("Játék indítása", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.width(10.dp))

        OutlinedButton(
            onClick = onMenu,
            enabled = canLaunch,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (menuFocused)
                    Color.White.copy(alpha = 0.18f)
                else Color.Transparent,
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.35f)
            ),
            border = BorderStroke(
                2.dp,
                if (menuFocused)
                    Color.White.copy(alpha = 0.95f)
                else Color.White.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .focusRequester(menuFR)
                .onFocusChanged { menuFocused = it.isFocused }
                .focusable()
        ) {
            Text("⋯", fontSize = 20.sp)
        }
    }
}
