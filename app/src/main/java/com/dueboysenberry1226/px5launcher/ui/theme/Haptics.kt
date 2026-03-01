package com.dueboysenberry1226.px5launcher.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {

    // ✅ 0..5 (0 = off). Default 1 = a mostani, legkisebb.
    @Volatile
    private var strengthLevel: Int = 1

    fun setStrength(level: Int) {
        strengthLevel = level.coerceIn(0, 5)
    }

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // finom (tick) - rövid, nem durva
    fun tick(context: Context) {
        val level = strengthLevel.coerceIn(0, 5)
        if (level == 0) return

        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return

        val (durMs, amp) = when (level) {
            1 -> 12 to 40   // ✅ régi “legkisebb”
            2 -> 12 to 55
            3 -> 13 to 70
            4 -> 14 to 90
            else -> 14 to 110 // 5: még mindig nem túl erős
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durMs.toLong(), amp))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durMs.toLong())
        }
    }

    // click - picit “határozottabb”, de 5-ön sem brutál
    fun click(context: Context) {
        val level = strengthLevel.coerceIn(0, 5)
        if (level == 0) return

        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return

        val (durMs, amp) = when (level) {
            1 -> 16 to 60   // ✅ régi “legkisebb”
            2 -> 16 to 80
            3 -> 17 to 100
            4 -> 18 to 120
            else -> 18 to 140 // 5: nem túl erős
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durMs.toLong(), amp))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durMs.toLong())
        }
    }
}