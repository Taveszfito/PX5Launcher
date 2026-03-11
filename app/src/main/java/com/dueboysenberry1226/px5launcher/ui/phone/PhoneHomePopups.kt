@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.data.PhoneCardType
import com.dueboysenberry1226.px5launcher.ui.widgets.WidgetPickerScreen
import com.dueboysenberry1226.px5launcher.ui.widgets.rememberWidgetPickerState

@Composable
internal fun BubbleMenu(
    title: String,
    onDismiss: () -> Unit,
    items: List<Pair<String, () -> Unit>>
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
                onLongClick = onDismiss
            )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 320.dp)
                .padding(18.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))

                items.forEach { (label, act) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
                        shape = RoundedCornerShape(16.dp),
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
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.92f),
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
    onConfirmAddCard: (rowsToShow: Int) -> Unit,
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

        // ✅ ÚJ: portrait home-on engedjük a nagyobb spanokat is
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
            .background(Color.Black.copy(alpha = 0.35f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
                onLongClick = onCancel
            )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1020).copy(alpha = 0.92f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(18.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TabPill("Kártyák", tab == 0) { onTabChange(0) }
                    Spacer(Modifier.width(10.dp))
                    TabPill("Widgetek", tab == 1) { onTabChange(1) }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Bezár",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCancel,
                                onLongClick = onCancel
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (tab == 0) {
                    Text(
                        text = "Válassz kártyát:",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))

                    CardChoiceRow("Naptár", selectedCard == PhoneCardType.CALENDAR) { onSelectCard(PhoneCardType.CALENDAR) }
                    CardChoiceRow("Zene", selectedCard == PhoneCardType.MUSIC) { onSelectCard(PhoneCardType.MUSIC) }
                    CardChoiceRow("Értesítések", selectedCard == PhoneCardType.NOTIFICATIONS) { onSelectCard(PhoneCardType.NOTIFICATIONS) }

                    if (!errorText.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = errorText,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    Row(Modifier.fillMaxWidth()) {
                        ActionPill("Mégse", onCancel, Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        ActionPill(
                            text = "Hozzáadás",
                            onClick = { onConfirmAddCard(MAX_ROWS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 420.dp, max = 620.dp)
                    ) {
                        WidgetPickerScreen(
                            state = pickerState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .height(36.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White.copy(alpha = if (selected) 0.95f else 0.75f),
                fontSize = 13.sp,
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
    val bg = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f)
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (selected) "✓" else "",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .height(40.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}