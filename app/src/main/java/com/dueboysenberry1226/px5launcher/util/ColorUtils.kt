package com.dueboysenberry1226.px5launcher.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun computeDominantColor(
    bmp: Bitmap?,
    fallback: Color = Color(0xFF101826)
): Color {
    if (bmp == null) return fallback

    return withContext(Dispatchers.Default) {
        val p = Palette.from(bmp).generate()
        val rgb = p.getVibrantColor(p.getDominantColor(fallback.value.toLong().toInt()))
        Color(rgb)
    }
}
