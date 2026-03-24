@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import com.dueboysenberry1226.px5launcher.ui.theme.WidgetPickerScreen
import com.dueboysenberry1226.px5launcher.ui.theme.rememberWidgetPickerState

private val PopupGlass = Color.DarkGray.copy(alpha = 0.8f)
private val PopupGlassStrong = Color.White.copy(alpha = 0.16f)
private val PopupGlassSoft = Color.White.copy(alpha = 0.08f)
private val PopupBaseLayer = Color.White.copy(alpha = 0f)
private val PopupBaseLayerBorder = Color.White.copy(alpha = 0.10f)
private val PopupText = Color.White.copy(alpha = 0.95f)
private val PopupSubText = Color.White.copy(alpha = 0.76f)

@Composable
internal fun BubbleMenu(
    title: String,
    onDismiss: () -> Unit,
    items: List<Pair<String, () -> Unit>>
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.1f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
                onLongClick = onDismiss
            )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = PopupGlass),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 340.dp)
                .padding(18.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    color = PopupText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(10.dp))

                items.forEach { (label, act) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PopupGlassSoft),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = act,
                                onLongClick = act
                            )
                    ) {
                        Box(
                            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                color = PopupText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddStuffPopup(
    tab: Int,
    onTabChange: (Int) -> Unit,
    selectedCard: PhoneCardType?,
    onSelectCard: (PhoneCardType) -> Unit,
    errorText: String?,
    onCancel: () -> Unit,
    onConfirmAddCard: () -> Unit,
    pm: PackageManager,
    cellDp: Dp,
    onPickWidget: (provider: AppWidgetProviderInfo, spanX: Int, spanY: Int) -> Unit
) {
    val context = LocalContext.current
    val widgetManager = remember { AppWidgetManager.getInstance(context) }

    val pickerState = rememberWidgetPickerState(
        pm = pm,
        appWidgetManager = widgetManager,
        cellWidthDp = cellDp,
        cellHeightDp = cellDp,
        cellGapXDp = 12.dp,
        cellGapYDp = 12.dp,
        maxSpanX = 4,
        maxSpanY = 5,
        filterOutOversize = false,
        onPick = { provider, sx, sy -> onPickWidget(provider, sx, sy) },
        onBack = { onCancel() },
        vibrationEnabled = true
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.36f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
                onLongClick = onCancel
            )
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C1C20).copy(alpha = 0.96f)
            ),
            shape = RoundedCornerShape(34.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.975f)
                .widthIn(max = 960.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 720.dp)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TopTabButton(
                        text = stringResource(R.string.phone_add_popup_tab_cards),
                        selected = tab == 0,
                        onClick = { onTabChange(0) },
                        modifier = Modifier.weight(1f)
                    )

                    TopTabButton(
                        text = stringResource(R.string.phone_add_popup_tab_widgets),
                        selected = tab == 1,
                        onClick = { onTabChange(1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(18.dp))

                if (tab == 0) {
                    CardContentArea(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val cardsListState = rememberLazyListState()

                        LazyColumn(
                            state = cardsListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            item {
                                CardChoiceRow(
                                    title = stringResource(R.string.phone_card_calendar),
                                    selected = selectedCard == PhoneCardType.CALENDAR,
                                    onClick = { onSelectCard(PhoneCardType.CALENDAR) }
                                )
                            }

                            item {
                                CardChoiceRow(
                                    title = stringResource(R.string.phone_card_music),
                                    selected = selectedCard == PhoneCardType.MUSIC,
                                    onClick = { onSelectCard(PhoneCardType.MUSIC) }
                                )
                            }

                            if (!errorText.isNullOrBlank()) {
                                item {
                                    Text(
                                        text = errorText,
                                        color = Color.White.copy(alpha = 0.78f),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BottomActionButton(
                            text = stringResource(R.string.common_add),
                            onClick = onConfirmAddCard,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        )

                        BottomActionButton(
                            text = stringResource(R.string.common_cancel),
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        )
                    }
                } else {
                    CardContentArea(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        WidgetPickerScreen(
                            state = pickerState,
                            modifier = Modifier.fillMaxSize(),
                            embeddedInPhonePopup = true
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    BottomActionButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardContentArea(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = PopupBaseLayer
            ),
            modifier = Modifier.matchParentSize()
        ) {}

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = PopupGlassSoft
            ),
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = content
            )
        }
    }
}

@Composable
private fun TopTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) PopupGlassStrong else PopupGlassSoft

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .height(58.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (selected) PopupText else PopupSubText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CardChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) PopupGlassStrong else PopupGlassSoft

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = PopupText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            if (selected) {
                Text(
                    text = "✓",
                    color = PopupSubText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BottomActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PopupGlass),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = PopupText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}