@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.Tab

@Composable
fun HomeTopBar(
    tab: Tab,
    clockText: String,
    onTabChange: (Tab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit = {},
    topBarFocused: Boolean = false,
    topBarIndex: Int = when (tab) {
        Tab.GAMES -> 0
        Tab.MEDIA -> 1
        Tab.NOTIFICATIONS -> 2
    }
) {
    val gamesSelected = if (topBarFocused) topBarIndex == 0 else tab == Tab.GAMES
    val mediaSelected = if (topBarFocused) topBarIndex == 1 else tab == Tab.MEDIA
    val notificationsSelected = if (topBarFocused) topBarIndex == 2 else tab == Tab.NOTIFICATIONS

    val gamesUnderline = topBarFocused && topBarIndex == 0
    val mediaUnderline = topBarFocused && topBarIndex == 1
    val notificationsUnderline = topBarFocused && topBarIndex == 2

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PSTab(
                text = stringResource(R.string.topbar_tab_apps),
                selected = gamesSelected,
                underline = gamesUnderline,
                focused = topBarFocused,
                onClick = { onTabChange(Tab.GAMES) }
            )
            Spacer(Modifier.width(18.dp))
            PSTab(
                text = stringResource(R.string.topbar_tab_media),
                selected = mediaSelected,
                underline = mediaUnderline,
                focused = topBarFocused,
                onClick = { onTabChange(Tab.MEDIA) }
            )
            Spacer(Modifier.width(18.dp))
            PSTab(
                text = stringResource(R.string.topbar_tab_notifications),
                selected = notificationsSelected,
                underline = notificationsUnderline,
                focused = topBarFocused,
                onClick = { onTabChange(Tab.NOTIFICATIONS) }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TopIcon("🔍", onSearch)
            Spacer(Modifier.width(10.dp))
            TopIcon("⚙️", onSettings)
            Spacer(Modifier.width(10.dp))
            TopIcon("🙂", onAccount)
            Spacer(Modifier.width(16.dp))
            Text(
                text = clockText,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PSTab(
    text: String,
    selected: Boolean,
    underline: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val textAlpha = when {
        selected -> 1f
        focused -> 0.45f
        else -> 0.55f
    }

    Box(
        modifier = Modifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = text,
                color = Color.White.copy(alpha = textAlpha),
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )

            Spacer(Modifier.height(6.dp))

            Box(
                Modifier
                    .height(2.dp)
                    .width(if (underline) 46.dp else 0.dp)
                    .background(if (underline) Color.White else Color.Transparent)
            )
        }
    }
}

@Composable
private fun TopIcon(
    symbol: String,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = symbol,
        fontSize = 16.sp,
        color = Color.White.copy(alpha = 0.9f),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .sizeIn(minWidth = 34.dp, minHeight = 34.dp)
            .focusProperties { canFocus = false }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            )
    )
}