@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.Tab
import kotlinx.coroutines.delay

private enum class TopBarTextSource {
    APPS,
    MEDIA,
    CLOCK
}

@Composable
fun HomeTopBar(
    tab: Tab,
    clockText: String,
    onTabChange: (Tab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    vibrationEnabled: Boolean = true,
    topBarFocused: Boolean = false,
    topBarIndex: Int = when (tab) {
        Tab.GAMES -> 0
        Tab.MEDIA -> 1
        Tab.NOTIFICATIONS -> 2
    }
) {
    val context = LocalContext.current

    val appsBaseText = stringResource(R.string.topbar_tab_apps)
    val mediaBaseText = stringResource(R.string.topbar_tab_media)
    val notificationsBaseText = stringResource(R.string.topbar_tab_notifications)

    var easterEggEnabled by remember { mutableStateOf(false) }
    var activationTapCount by remember { mutableIntStateOf(0) }
    var resetTapCount by remember { mutableIntStateOf(0) }
    var showEasterEggMessage by remember { mutableStateOf(false) }

    var shuffledOrder by remember {
        mutableStateOf(
            listOf(
                TopBarTextSource.APPS,
                TopBarTextSource.MEDIA,
                TopBarTextSource.CLOCK
            )
        )
    }

    fun textForSource(source: TopBarTextSource): String {
        return when (source) {
            TopBarTextSource.APPS -> appsBaseText
            TopBarTextSource.MEDIA -> mediaBaseText
            TopBarTextSource.CLOCK -> clockText
        }
    }

    fun makeRandomOrder(): List<TopBarTextSource> {
        val defaultOrder = listOf(
            TopBarTextSource.APPS,
            TopBarTextSource.MEDIA,
            TopBarTextSource.CLOCK
        )

        var candidate = defaultOrder.shuffled()
        repeat(10) {
            if (candidate != defaultOrder) return candidate
            candidate = defaultOrder.shuffled()
        }

        return listOf(
            TopBarTextSource.MEDIA,
            TopBarTextSource.CLOCK,
            TopBarTextSource.APPS
        )
    }

    fun resetTapProgress() {
        activationTapCount = 0
        resetTapCount = 0
    }

    fun handleNotificationsTap() {
        if (vibrationEnabled) Haptics.click(context)

        if (!easterEggEnabled) {
            activationTapCount += 1
            resetTapCount = 0

            if (activationTapCount >= 5) {
                easterEggEnabled = true
                activationTapCount = 0
                resetTapCount = 0
                shuffledOrder = makeRandomOrder()
                showEasterEggMessage = true
            }
        } else {
            resetTapCount += 1
            activationTapCount = 0

            if (resetTapCount >= 3) {
                easterEggEnabled = false
                activationTapCount = 0
                resetTapCount = 0
                shuffledOrder = listOf(
                    TopBarTextSource.APPS,
                    TopBarTextSource.MEDIA,
                    TopBarTextSource.CLOCK
                )
            }
        }

        onTabChange(Tab.NOTIFICATIONS)
    }

    fun handleOtherTopBarAction(action: () -> Unit) {
        if (vibrationEnabled) Haptics.click(context)
        resetTapProgress()
        action()
    }

    val displayedAppsText = if (easterEggEnabled) {
        textForSource(shuffledOrder[0])
    } else {
        appsBaseText
    }

    val displayedMediaText = if (easterEggEnabled) {
        textForSource(shuffledOrder[1])
    } else {
        mediaBaseText
    }

    val displayedClockText = if (easterEggEnabled) {
        textForSource(shuffledOrder[2])
    } else {
        clockText
    }

    val gamesSelected = if (topBarFocused) topBarIndex == 0 else tab == Tab.GAMES
    val mediaSelected = if (topBarFocused) topBarIndex == 1 else tab == Tab.MEDIA
    val notificationsSelected = if (topBarFocused) topBarIndex == 2 else tab == Tab.NOTIFICATIONS

    val searchSelected = topBarFocused && topBarIndex == 3
    val settingsSelected = topBarFocused && topBarIndex == 4

    val gamesUnderline = topBarFocused && topBarIndex == 0
    val mediaUnderline = topBarFocused && topBarIndex == 1
    val notificationsUnderline = topBarFocused && topBarIndex == 2

    LaunchedEffect(showEasterEggMessage) {
        if (showEasterEggMessage) {
            delay(5000)
            showEasterEggMessage = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = showEasterEggMessage,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.easter_egg_topbar_shuffle_found),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PSTab(
                    text = displayedAppsText,
                    selected = gamesSelected,
                    underline = gamesUnderline,
                    focused = topBarFocused,
                    onClick = {
                        handleOtherTopBarAction {
                            onTabChange(Tab.GAMES)
                        }
                    }
                )

                Spacer(Modifier.width(18.dp))

                PSTab(
                    text = displayedMediaText,
                    selected = mediaSelected,
                    underline = mediaUnderline,
                    focused = topBarFocused,
                    onClick = {
                        handleOtherTopBarAction {
                            onTabChange(Tab.MEDIA)
                        }
                    }
                )

                Spacer(Modifier.width(18.dp))

                PSTab(
                    text = notificationsBaseText,
                    selected = notificationsSelected,
                    underline = notificationsUnderline,
                    focused = topBarFocused,
                    onClick = {
                        handleNotificationsTap()
                    }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopIcon(
                    icon = Icons.Filled.Search,
                    contentDescription = null,
                    selected = searchSelected,
                    topBarFocused = topBarFocused,
                    onClick = {
                        handleOtherTopBarAction {
                            onSearch()
                        }
                    }
                )

                Spacer(Modifier.width(10.dp))

                TopIcon(
                    icon = Icons.Filled.Settings,
                    contentDescription = null,
                    selected = settingsSelected,
                    topBarFocused = topBarFocused,
                    onClick = {
                        handleOtherTopBarAction {
                            onSettings()
                        }
                    }
                )

                Spacer(Modifier.width(16.dp))

                Text(
                    text = displayedClockText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
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

        Spacer(Modifier.height(0.dp))

        Box(
            modifier = Modifier
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