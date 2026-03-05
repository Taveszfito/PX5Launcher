package com.dueboysenberry1226.px5launcher.ui.phone

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import com.dueboysenberry1226.px5launcher.util.computeDominantColor

internal suspend fun setAmbientFromApp(
    ambientColor: MutableState<Color>,
    app: AppItem?
) {
    if (app == null) return
    val bmp = drawableToBitmap(app.icon) ?: return
    val c: Color = computeDominantColor(bmp)
    ambientColor.value = if (c.alpha <= 0.01f) Color(0xFF101826) else c
}