package com.dueboysenberry1226.px5launcher

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dueboysenberry1226.px5launcher.ui.PSHomeRoute
import com.dueboysenberry1226.px5launcher.ui.settings.SettingsScreen

private enum class RootScreen { HOME, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableImmersiveFullscreen(window)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {

                    var rootScreen by rememberSaveable { mutableStateOf(RootScreen.HOME) }

                    // extra safety: Settingsből back mindig Home
                    BackHandler(enabled = rootScreen == RootScreen.SETTINGS) {
                        rootScreen = RootScreen.HOME
                    }

                    when (rootScreen) {
                        RootScreen.HOME -> {
                            PSHomeRoute(
                                pm = packageManager,
                                onOpenSettings = {
                                    rootScreen = RootScreen.SETTINGS
                                }
                            )
                        }

                        RootScreen.SETTINGS -> {
                            SettingsScreen(
                                onBackToHome = {
                                    rootScreen = RootScreen.HOME
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun enableImmersiveFullscreen(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}