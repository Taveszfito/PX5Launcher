package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import com.dueboysenberry1226.px5launcher.R

@Composable
fun BottomPanelTabsRow(
    active: Any,
    modifier: Modifier = Modifier
) {
    fun isActive(name: String) = active.toString().contains(name, ignoreCase = true)

    val widgetsAlpha = if (isActive("WIDGETS")) 1f else 0.45f
    val calendarAlpha = if (isActive("CALENDAR")) 1f else 0.45f
    val musicAlpha = if (isActive("MUSIC")) 1f else 0.45f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.bottompanel_hint_lb),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(14.dp))

        Text(
            text = stringResource(R.string.bottompanel_tab_widgets),
            color = Color.White.copy(alpha = widgetsAlpha),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(14.dp))

        Text(
            text = stringResource(R.string.bottompanel_tab_calendar),
            color = Color.White.copy(alpha = calendarAlpha),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(14.dp))

        Text(
            text = stringResource(R.string.bottompanel_tab_music),
            color = Color.White.copy(alpha = musicAlpha),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(14.dp))

        Text(
            text = stringResource(R.string.bottompanel_hint_rb),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}