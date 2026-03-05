package com.dueboysenberry1226.px5launcher.ui.phone

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

internal fun drawableToBitmap(d: Drawable?): Bitmap? {
    if (d == null) return null
    return try {
        val w = d.intrinsicWidth.coerceAtLeast(1)
        val h = d.intrinsicHeight.coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, c.width, c.height)
        d.draw(c)
        b
    } catch (_: Throwable) {
        null
    }
}