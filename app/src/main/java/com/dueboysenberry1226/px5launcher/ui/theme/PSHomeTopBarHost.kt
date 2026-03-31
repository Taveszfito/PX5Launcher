@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dueboysenberry1226.px5launcher.data.Tab
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PSHomeTopBarHost(
    tab: Tab,
    is24h: Boolean,
    onTabChange: (Tab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    topBarFocused: Boolean,
    topBarIndex: Int
) {
    var clockText by remember { mutableStateOf("") }

    LaunchedEffect(is24h) {
        val pattern = if (is24h) "HH:mm" else "hh:mm a"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())

        while (true) {
            clockText = fmt.format(Date())
            delay(1_000)
        }
    }

    HomeTopBar(
        tab = tab,
        clockText = clockText,
        onTabChange = onTabChange,
        onSearch = onSearch,
        onSettings = onSettings,
        topBarFocused = topBarFocused,
        topBarIndex = topBarIndex
    )
}