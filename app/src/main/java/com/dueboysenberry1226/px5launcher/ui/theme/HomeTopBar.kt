@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    vibrationEnabled: Boolean = true,
    topBarFocused: Boolean = false,
    // 0..2 tabok, 3=search, 4=settings
    topBarIndex: Int = when (tab) {
        Tab.GAMES -> 0
        Tab.MEDIA -> 1
        Tab.NOTIFICATIONS -> 2
    }
) {
    val context = LocalContext.current

    val gamesSelected = if (topBarFocused) topBarIndex == 0 else tab == Tab.GAMES
    val mediaSelected = if (topBarFocused) topBarIndex == 1 else tab == Tab.MEDIA
    val notificationsSelected = if (topBarFocused) topBarIndex == 2 else tab == Tab.NOTIFICATIONS

    val searchSelected = topBarFocused && topBarIndex == 3
    val settingsSelected = topBarFocused && topBarIndex == 4

    val gamesUnderline = topBarFocused && topBarIndex == 0
    val mediaUnderline = topBarFocused && topBarIndex == 1
    val notificationsUnderline = topBarFocused && topBarIndex == 2

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically // ✅ vissza a régi viselkedésre
    ) {

        // LEFT SIDE (Tabs) - ✅ referencia magasság (szöveg ott marad ahol régen volt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PSTab(
                text = stringResource(R.string.topbar_tab_apps),
                selected = gamesSelected,
                underline = gamesUnderline,
                focused = topBarFocused,
                onClick = {
                    if (vibrationEnabled) Haptics.click(context)
                    onTabChange(Tab.GAMES)
                }
            )

            Spacer(Modifier.width(18.dp))

            PSTab(
                text = stringResource(R.string.topbar_tab_media),
                selected = mediaSelected,
                underline = mediaUnderline,
                focused = topBarFocused,
                onClick = {
                    if (vibrationEnabled) Haptics.click(context)
                    onTabChange(Tab.MEDIA)
                }
            )

            Spacer(Modifier.width(18.dp))

            PSTab(
                text = stringResource(R.string.topbar_tab_notifications),
                selected = notificationsSelected,
                underline = notificationsUnderline,
                focused = topBarFocused,
                onClick = {
                    if (vibrationEnabled) Haptics.click(context)
                    onTabChange(Tab.NOTIFICATIONS)
                }
            )
        }

        // RIGHT SIDE (Icons + Clock) - ✅ mindet a tab szövegéhez igazítjuk
        Row(
            modifier = Modifier.padding(top = 0.dp), // 🔧 EZ AZ EGY CSAVAR: 0..3 dp között állítható
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopIcon(
                icon = Icons.Filled.Search,
                contentDescription = "Search",
                selected = searchSelected,
                topBarFocused = topBarFocused,
                onClick = {
                    if (vibrationEnabled) Haptics.click(context)
                    onSearch()
                }
            )

            Spacer(Modifier.width(10.dp))

            TopIcon(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                selected = settingsSelected,
                topBarFocused = topBarFocused,
                onClick = {
                    if (vibrationEnabled) Haptics.click(context)
                    onSettings()
                }
            )

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

    Column(
        modifier = Modifier
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            )
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = textAlpha),
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )

        Spacer(Modifier.height(0.dp)) // kisebb mint volt

        Box(
            Modifier
                .height(2.dp)
                .width(if (underline) 46.dp else 0.dp)
                .background(if (underline) Color.White else Color.Transparent)
        )
    }
}


@Composable
private fun TopIcon(
    icon: ImageVector,
    contentDescription: String?,
    selected: Boolean,
    topBarFocused: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)

    val bgA = if (selected) 0.18f else 0f
    val borderA = if (selected) 0.55f else 0f
    val iconA = when {
        selected -> 0.92f
        topBarFocused -> 0.55f
        else -> 0.85f
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = bgA))
            .border(2.dp, Color.White.copy(alpha = borderA), shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = iconA),
            modifier = Modifier.size(18.dp)
        )
    }
}