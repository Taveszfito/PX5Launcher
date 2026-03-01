package com.dueboysenberry1226.px5launcher

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dueboysenberry1226.px5launcher.data.SettingsRepository
import com.dueboysenberry1226.px5launcher.ui.PSHomeRoute
import com.dueboysenberry1226.px5launcher.ui.phone.PhoneHomeRoute
import com.dueboysenberry1226.px5launcher.ui.settings.SettingsScreen
import com.dueboysenberry1226.px5launcher.util.updateLocale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private enum class RootScreen { HOME, SETTINGS }

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = runBlocking {
            SettingsRepository(newBase).languageCodeFlow.first()
        }
        super.attachBaseContext(newBase.updateLocale(lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔒 Globális BACK blokkolás (ne zárja be az appot)
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            val config = LocalConfiguration.current
            val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT

            // ✅ Portrait: NEM immersive, Landscape: immersive
            SideEffect {
                setImmersiveFullscreen(window, enabled = !isPortrait)
            }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {

                    var rootScreen by rememberSaveable { mutableStateOf(RootScreen.HOME) }

                    BackHandler(enabled = rootScreen == RootScreen.SETTINGS) {
                        rootScreen = RootScreen.HOME
                    }

                    when (rootScreen) {
                        RootScreen.HOME -> {
                            if (isPortrait) {
                                PhoneHomeRoute(
                                    pm = packageManager,
                                    onOpenSettings = { rootScreen = RootScreen.SETTINGS }
                                )
                            } else {
                                PSHomeRoute(
                                    pm = packageManager,
                                    onOpenSettings = { rootScreen = RootScreen.SETTINGS }
                                )
                            }
                        }

                        RootScreen.SETTINGS -> {
                            SettingsScreen(onBackToHome = { rootScreen = RootScreen.HOME })
                        }
                    }
                }
            }
        }
    }
}

private fun setImmersiveFullscreen(window: Window, enabled: Boolean) {
    if (enabled) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
}