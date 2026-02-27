package com.dueboysenberry1226.px5launcher.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

fun Context.updateLocale(languageCode: String): Context {
    val locale = when (languageCode) {
        "hu" -> Locale("hu")
        "en" -> Locale("en")
        else -> Locale.getDefault()
    }

    Locale.setDefault(locale)

    val config = Configuration(resources.configuration)
    config.setLocale(locale)

    return createConfigurationContext(config)
}