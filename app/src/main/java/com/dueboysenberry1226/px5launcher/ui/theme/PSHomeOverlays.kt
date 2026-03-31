@file:OptIn(ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")

package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.yield
import androidx.compose.ui.Alignment

@Composable
internal fun PSHomeGamesOverlays(
    searchOpen: Boolean,
    searchQuery: String,
    searchResults: List<com.dueboysenberry1226.px5launcher.data.LaunchableApp>,
    menuOpen: Boolean,
    isPinned: Boolean,
    canUninstall: Boolean,
    canDisable: Boolean,
    onSearchChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchLaunch: (com.dueboysenberry1226.px5launcher.data.LaunchableApp) -> Unit,
    onTogglePin: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDisable: () -> Unit,
    onMenuClose: () -> Unit
) {
    HomeOverlays(
        searchOpen = searchOpen,
        searchQuery = searchQuery,
        searchResults = searchResults,
        menuOpen = menuOpen,
        isPinned = isPinned,
        canUninstall = canUninstall,
        canDisable = canDisable,
        onSearchChange = onSearchChange,
        onSearchClose = onSearchClose,
        onSearchLaunch = onSearchLaunch,
        onTogglePin = onTogglePin,
        onAppInfo = onAppInfo,
        onUninstall = onUninstall,
        onDisable = onDisable,
        onMenuClose = onMenuClose
    )
}

@Composable
internal fun PSHomeWidgetPickerOverlay(
    showWidgetPicker: Boolean,
    content: @Composable () -> Unit
) {
    val widgetPickerFR = remember { FocusRequester() }

    AnimatedVisibility(
        visible = showWidgetPicker,
        enter = fadeIn(animationSpec = tween(160)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(130)) + scaleOut(targetScale = 0.94f, animationSpec = tween(170))
    ) {
        LaunchedEffect(Unit) {
            yield()
            widgetPickerFR.requestFocus()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(1f)
                    .fillMaxHeight(1f)
                    .focusRequester(widgetPickerFR)
                    .focusable()
            ) {
                content()
            }
        }
    }
}
