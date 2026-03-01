@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.settings

import com.dueboysenberry1226.px5launcher.ui.Haptics
import android.app.Activity
import androidx.compose.foundation.focusable
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.ButtonLayout
import com.dueboysenberry1226.px5launcher.data.SettingsRepository
import com.dueboysenberry1226.px5launcher.data.WidgetsRepository
import kotlinx.coroutines.launch
import kotlin.math.max

private enum class FocusZone { LEFT, RIGHT }

private sealed class SettingRow(val title: String, val subtitle: String? = null) {

    class Toggle(
        title: String,
        subtitle: String? = null,
        val get: () -> Boolean,
        val set: suspend (Boolean) -> Unit
    ) : SettingRow(title, subtitle)

    class ExpandableSliderRow(
        title: String,
        subtitle: String? = null,
        val min: Int,
        val max: Int,
        val step: Int,
        val get: () -> Int,
        val set: suspend (Int) -> Unit
    ) : SettingRow(title, subtitle)

    class Action(
        title: String,
        subtitle: String? = null,
        val onClick: suspend () -> Unit
    ) : SettingRow(title, subtitle)

    class Picker(
        title: String,
        subtitle: String? = null,
        val options: List<String>,
        val getIndex: () -> Int,
        val setIndex: suspend (Int) -> Unit
    ) : SettingRow(title, subtitle)
}

private fun snapToStep(raw: Int, min: Int, max: Int, step: Int): Int {
    if (step <= 1) return raw.coerceIn(min, max)
    val clamped = raw.coerceIn(min, max)
    val offset = clamped - min
    val snapped = ((offset + step / 2) / step) * step + min
    return snapped.coerceIn(min, max)
}

@Composable
fun SettingsScreen(
    onBackToHome: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settingsRepo = remember(context) { SettingsRepository(context) }
    val widgetsRepo = remember(context) { WidgetsRepository(context) }

    // ---- values ----
    val clock24h by settingsRepo.clock24hFlow.collectAsState(initial = true)
    val showBigName by settingsRepo.showBigAppNameFlow.collectAsState(initial = true)
    val accentFromIcon by settingsRepo.accentFromAppIconFlow.collectAsState(initial = true)
    val languageCode by settingsRepo.languageCodeFlow.collectAsState(initial = "system")

    val ambientEnabled by settingsRepo.ambientEnabledFlow.collectAsState(initial = false)
    val ambientMuted by settingsRepo.ambientMutedFlow.collectAsState(initial = false)
    val ambientVolume by settingsRepo.ambientVolumeFlow.collectAsState(initial = 35)
    val ambientSoundUri by settingsRepo.ambientSoundUriFlow.collectAsState(initial = null)

    val clickEnabled by settingsRepo.clickEnabledFlow.collectAsState(initial = true)
    val clickVolume by settingsRepo.clickVolumeFlow.collectAsState(initial = 35)

    val buttonLayout by settingsRepo.buttonLayoutFlow.collectAsState(initial = ButtonLayout.PS)
    val vibrationEnabled by settingsRepo.vibrationEnabledFlow.collectAsState(initial = true)
    val vibrationStrength by settingsRepo.vibrationStrengthFlow.collectAsState(initial = 1)

    val allAppsColumns by settingsRepo.allAppsColumnsFlow.collectAsState(initial = 4)
    val recentsMax by settingsRepo.recentsMaxFlow.collectAsState(initial = 30)

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }
    fun hTick() {
        if (vibrationEnabled) Haptics.tick(context)
    }

    // ✅ Globális haptics erősség bekötés
    LaunchedEffect(vibrationStrength) {
        Haptics.setStrength(vibrationStrength)
    }

    val ambientPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch { settingsRepo.setAmbientSoundUri(uri.toString()) }
        }
    }

    // ---- categories ----
    val categories = listOf(
        stringResource(R.string.settings_category_general),
        stringResource(R.string.settings_category_audio),
        stringResource(R.string.settings_category_controls),
        stringResource(R.string.settings_category_home),
        stringResource(R.string.settings_category_permissions),
        stringResource(R.string.settings_category_about)
    )
    val backIndexLeft = categories.size

    var leftIndex by remember { mutableIntStateOf(0) }

    var catIndex by remember { mutableIntStateOf(0) }
    var rowIndex by remember { mutableIntStateOf(0) }
    var focusZone by remember { mutableStateOf(FocusZone.LEFT) }

    var expandedRowIndex by remember { mutableStateOf<Int?>(null) }
    var expandedOptionIndex by remember { mutableIntStateOf(0) }

    val rightListState = rememberLazyListState()
    var rightAutoScrollTick by remember { mutableIntStateOf(0) }
    val leftListState = rememberLazyListState()

    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }

    val languageOptions = listOf(
        stringResource(R.string.common_system),
        stringResource(R.string.settings_language_hungarian),
        stringResource(R.string.common_english)
    )

    fun langIndexFromCode(code: String): Int = when (code) {
        "hu" -> 1
        "en" -> 2
        else -> 0
    }

    fun codeFromLangIndex(i: Int): String = when (i) {
        1 -> "hu"
        2 -> "en"
        else -> "system"
    }

    fun openAppDetails() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isNotificationListenerEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val target = ComponentName(
            ctx,
            com.dueboysenberry1226.px5launcher.media.PX5NotificationListener::class.java
        ).flattenToString()
        return enabled.contains(target)
    }

    fun toggle(title: String, subtitle: String? = null, get: () -> Boolean, set: suspend (Boolean) -> Unit) =
        SettingRow.Toggle(title, subtitle, get, set)

    fun expandableSlider(
        title: String,
        subtitle: String? = null,
        min: Int,
        max: Int,
        step: Int,
        get: () -> Int,
        set: suspend (Int) -> Unit
    ) = SettingRow.ExpandableSliderRow(title, subtitle, min, max, step, get, set)

    fun action(title: String, subtitle: String? = null, onClick: suspend () -> Unit) =
        SettingRow.Action(title, subtitle, onClick)

    fun picker(
        title: String,
        subtitle: String? = null,
        options: List<String>,
        getIndex: () -> Int,
        setIndex: suspend (Int) -> Unit
    ) = SettingRow.Picker(title, subtitle, options, getIndex, setIndex)

    val rowsForCategory: List<SettingRow> = when (catIndex) {
        0 -> listOf(
            toggle(
                stringResource(R.string.settings_clock_24h_title),
                stringResource(R.string.settings_clock_24h_subtitle),
                { clock24h },
                { settingsRepo.setClock24h(it) }
            ),
            toggle(
                stringResource(R.string.settings_big_app_name_title),
                stringResource(R.string.settings_big_app_name_subtitle),
                { showBigName },
                { settingsRepo.setShowBigAppName(it) }
            ),
            toggle(
                stringResource(R.string.settings_accent_from_icon_title),
                stringResource(R.string.settings_accent_from_icon_subtitle),
                { accentFromIcon },
                { settingsRepo.setAccentFromAppIcon(it) }
            ),
            picker(
                stringResource(R.string.settings_language_title),
                stringResource(R.string.settings_language_subtitle),
                languageOptions,
                getIndex = { langIndexFromCode(languageCode) },
                setIndex = {
                    settingsRepo.setLanguageCode(codeFromLangIndex(it))
                    (context as? Activity)?.recreate()
                }
            ),
            action(
                stringResource(R.string.settings_widgets_reset_title),
                stringResource(R.string.settings_widgets_reset_subtitle)
            ) { widgetsRepo.clearAll() }
        )

        1 -> listOf(
            toggle(
                stringResource(R.string.settings_ambient_title),
                stringResource(R.string.settings_ambient_subtitle),
                { ambientEnabled },
                { settingsRepo.setAmbientEnabled(it) }
            ),
            toggle(
                stringResource(R.string.settings_ambient_mute_title),
                stringResource(R.string.settings_ambient_mute_subtitle),
                { ambientMuted },
                { settingsRepo.setAmbientMuted(it) }
            ),
            // ✅ MOST MÁR EXPANDABLE SLIDER
            expandableSlider(
                stringResource(R.string.settings_ambient_volume_title),
                "0-100",
                0, 100, 5,
                { ambientVolume },
                { settingsRepo.setAmbientVolume(it) }
            ),
            action(
                stringResource(R.string.settings_ambient_change_title),
                ambientSoundUri?.let { stringResource(R.string.common_selected) } ?: stringResource(R.string.common_not_selected)
            ) { ambientPicker.launch(arrayOf("audio/*")) },
            toggle(
                stringResource(R.string.settings_click_sound_title),
                stringResource(R.string.settings_click_sound_subtitle),
                { clickEnabled },
                { settingsRepo.setClickEnabled(it) }
            ),
            // ✅ MOST MÁR EXPANDABLE SLIDER
            expandableSlider(
                stringResource(R.string.settings_click_volume_title),
                "0-100",
                0, 100, 5,
                { clickVolume },
                { settingsRepo.setClickVolume(it) }
            )
        )

        2 -> listOf(
            picker(
                title = stringResource(R.string.settings_button_layout_title),
                subtitle = stringResource(R.string.settings_button_layout_subtitle),
                options = listOf(
                    stringResource(R.string.settings_layout_playstation),
                    stringResource(R.string.common_xbox),
                    stringResource(R.string.common_tv)
                ),
                getIndex = {
                    when (buttonLayout) {
                        ButtonLayout.PS -> 0
                        ButtonLayout.XBOX -> 1
                        ButtonLayout.TV -> 2
                    }
                },
                setIndex = {
                    val v = when (it) {
                        1 -> ButtonLayout.XBOX
                        2 -> ButtonLayout.TV
                        else -> ButtonLayout.PS
                    }
                    settingsRepo.setButtonLayout(v)
                }
            ),
            toggle(
                stringResource(R.string.settings_vibration_title),
                stringResource(R.string.settings_vibration_subtitle),
                { vibrationEnabled },
                { settingsRepo.setVibrationEnabled(it) }
            ),
            expandableSlider(
                title = stringResource(R.string.settings_vibration_strength_title),
                subtitle = stringResource(R.string.settings_vibration_strength_subtitle),
                min = 0,
                max = 5,
                step = 1,
                get = { vibrationStrength },
                set = { newValue ->
                    val v = newValue.coerceIn(0, 5)
                    settingsRepo.setVibrationStrength(v)
                    if (v == 0) settingsRepo.setVibrationEnabled(false) else settingsRepo.setVibrationEnabled(true)
                }
            )
        )

        3 -> listOf(
            // ✅ MOST MÁR EXPANDABLE SLIDER
            expandableSlider(
                stringResource(R.string.settings_allapps_columns_title),
                "2 - 5",
                2, 5, 1,
                { allAppsColumns },
                { settingsRepo.setAllAppsColumns(it) }
            ),
            // ✅ MOST MÁR EXPANDABLE SLIDER
            expandableSlider(
                stringResource(R.string.settings_recents_max_title),
                stringResource(R.string.settings_recents_max_subtitle),
                5, 200, 5,
                { recentsMax },
                { settingsRepo.setRecentsMax(it) }
            )
        )

        4 -> listOf(
            action(
                stringResource(R.string.settings_notifications_permission_title),
                if (isNotificationListenerEnabled(context)) stringResource(R.string.common_active) else stringResource(R.string.common_not_enabled)
            ) { openNotificationListenerSettings() },
            action(
                stringResource(R.string.settings_storage_permission_title),
                stringResource(R.string.settings_storage_permission_subtitle)
            ) { openAppDetails() },
            action(
                stringResource(R.string.settings_system_settings_title),
                stringResource(R.string.settings_android_settings_subtitle)
            ) { openSystemSettings() }
        )

        else -> listOf(
            action(
                stringResource(R.string.settings_category_about),
                stringResource(R.string.common_app_info)
            ) { openAppDetails() }
        )
    }

    fun clampRowIndex() {
        val maxIdx = (rowsForCategory.size - 1).coerceAtLeast(0)
        rowIndex = rowIndex.coerceIn(0, maxIdx)
        if (expandedRowIndex != null && expandedRowIndex !in 0..maxIdx) {
            expandedRowIndex = null
        }
    }

    fun selectCategory(newCat: Int) {
        catIndex = newCat.coerceIn(0, categories.lastIndex)
        rowIndex = 0
        expandedRowIndex = null
        expandedOptionIndex = 0
        clampRowIndex()
        rightAutoScrollTick++
    }

    LaunchedEffect(catIndex, rowsForCategory.size) { clampRowIndex() }

    LaunchedEffect(leftIndex, focusZone) {
        if (focusZone == FocusZone.LEFT && leftIndex in 0..categories.lastIndex) {
            leftListState.animateScrollToItem(leftIndex)
        }
    }

    LaunchedEffect(catIndex, rightAutoScrollTick, focusZone) {
        if (focusZone == FocusZone.RIGHT) {
            val target = rowIndex.coerceIn(0, max(0, rowsForCategory.lastIndex))
            rightListState.animateScrollToItem(index = target, scrollOffset = 0)
        }
    }

    fun expandRowIfPossible(index: Int) {
        val row = rowsForCategory.getOrNull(index) ?: return
        when (row) {
            is SettingRow.Toggle -> {
                expandedRowIndex = index
                expandedOptionIndex = if (row.get()) 1 else 0
            }
            is SettingRow.Picker -> {
                expandedRowIndex = index
                expandedOptionIndex = row.getIndex().coerceIn(0, row.options.lastIndex)
            }
            is SettingRow.ExpandableSliderRow -> {
                expandedRowIndex = index
                expandedOptionIndex = row.get().coerceIn(row.min, row.max)
            }
            else -> {
                expandedRowIndex = null
                expandedOptionIndex = 0
            }
        }
    }

    fun collapseExpanded() {
        expandedRowIndex = null
        expandedOptionIndex = 0
    }

    fun commitExpandedSelection(forRowIndex: Int? = null, optionIndex: Int? = null) {
        val idx = forRowIndex ?: expandedRowIndex ?: return
        val row = rowsForCategory.getOrNull(idx) ?: return
        val opt = optionIndex ?: expandedOptionIndex

        scope.launch {
            when (row) {
                is SettingRow.Toggle -> row.set(opt == 1)
                is SettingRow.Picker -> row.setIndex(opt.coerceIn(0, row.options.lastIndex))
                is SettingRow.ExpandableSliderRow -> row.set(opt.coerceIn(row.min, row.max))
                else -> Unit
            }
        }
        // Toggle/Picker esetén csukjuk, slider esetén maradhatna – de egységesen csukjuk Enterre.
        collapseExpanded()
    }

    fun onKey(e: KeyEvent): Boolean {
        val nk = e.nativeKeyEvent
        if (nk.action != AndroidKeyEvent.ACTION_DOWN) return false

        val isExpanded = (expandedRowIndex == rowIndex)
        val currentRow = rowsForCategory.getOrNull(rowIndex)

        // ✅ Slider ütközőknél TICK, máshol CLICK
        val isSliderStepKey =
            focusZone == FocusZone.RIGHT &&
                    (nk.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT || nk.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT) &&
                    (isExpanded && currentRow is SettingRow.ExpandableSliderRow)

        if (vibrationEnabled) {
            when (nk.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isSliderStepKey) Haptics.tick(context) else Haptics.click(context)
                }

                AndroidKeyEvent.KEYCODE_DPAD_UP,
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                    Haptics.click(context)
                }
            }
        }

        when (nk.keyCode) {
            AndroidKeyEvent.KEYCODE_BACK,
            AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                hClick()
                onBackToHome()
                return true
            }
        }

        if (focusZone == FocusZone.LEFT) {
            when (nk.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    leftIndex = (leftIndex - 1).coerceAtLeast(0)
                    if (leftIndex != backIndexLeft) selectCategory(leftIndex)
                    return true
                }
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    leftIndex = (leftIndex + 1).coerceAtMost(backIndexLeft)
                    if (leftIndex != backIndexLeft) selectCategory(leftIndex)
                    return true
                }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (leftIndex == backIndexLeft) return true
                    focusZone = FocusZone.RIGHT
                    clampRowIndex()
                    rightAutoScrollTick++
                    return true
                }
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                    if (leftIndex == backIndexLeft) {
                        onBackToHome()
                    } else {
                        focusZone = FocusZone.RIGHT
                        clampRowIndex()
                        rightAutoScrollTick++
                    }
                    return true
                }
            }
            return false
        }

        // RIGHT zone
        when (nk.keyCode) {

            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isExpanded) {
                    when (val row = currentRow) {
                        is SettingRow.Toggle -> expandedOptionIndex =
                            (expandedOptionIndex - 1).coerceAtLeast(0).coerceIn(0, 1)

                        is SettingRow.Picker -> expandedOptionIndex =
                            (expandedOptionIndex - 1).coerceAtLeast(0).coerceIn(0, row.options.lastIndex)

                        is SettingRow.ExpandableSliderRow -> {
                            expandedOptionIndex = (expandedOptionIndex - row.step).coerceIn(row.min, row.max)
                            scope.launch { row.set(expandedOptionIndex) }
                        }

                        else -> {}
                    }
                    return true
                }

                focusZone = FocusZone.LEFT
                collapseExpanded()
                return true
            }

            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isExpanded) {
                    when (val row = currentRow) {
                        is SettingRow.Toggle -> expandedOptionIndex = (expandedOptionIndex + 1).coerceAtMost(1)
                        is SettingRow.Picker -> expandedOptionIndex = (expandedOptionIndex + 1).coerceAtMost(row.options.lastIndex)
                        is SettingRow.ExpandableSliderRow -> {
                            expandedOptionIndex = (expandedOptionIndex + row.step).coerceIn(row.min, row.max)
                            scope.launch { row.set(expandedOptionIndex) }
                        }
                        else -> {}
                    }
                    return true
                }
                return true
            }

            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                rowIndex = (rowIndex - 1).coerceAtLeast(0)
                collapseExpanded()
                rightAutoScrollTick++
                return true
            }

            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                rowIndex = (rowIndex + 1).coerceAtMost(rowsForCategory.lastIndex)
                collapseExpanded()
                rightAutoScrollTick++
                return true
            }

            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                val row = rowsForCategory.getOrNull(rowIndex) ?: return true

                if (isExpanded) {
                    commitExpandedSelection()
                    return true
                }

                if (row is SettingRow.Action) {
                    scope.launch { row.onClick() }
                    return true
                }

                // ✅ minden slider is ide esik: Enter -> nyit/zár
                if (row is SettingRow.Toggle || row is SettingRow.Picker || row is SettingRow.ExpandableSliderRow) {
                    if (expandedRowIndex == rowIndex) collapseExpanded() else expandRowIfPossible(rowIndex)
                    return true
                }

                return true
            }
        }

        return false
    }

    val bg = remember {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0B1020),
                Color(0xFF070A12),
                Color(0xFF05070D)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { onKey(it) }
            .padding(18.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.common_settings),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = stringResource(R.string.settings_experimental_notice),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = stringResource(R.string.common_back_hint),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxSize()) {

                // LEFT
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp)) {
                        LazyColumn(
                            state = leftListState,
                            modifier = Modifier.weight(1f),
                            userScrollEnabled = true
                        ) {
                            itemsIndexed(categories) { i, title ->
                                val selected = (i == leftIndex)
                                val focused = (focusZone == FocusZone.LEFT)
                                val alpha = if (selected) 1f else if (focused) 0.60f else 0.45f

                                Text(
                                    text = title,
                                    color = Color.White.copy(alpha = alpha),
                                    fontSize = 16.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp, horizontal = 10.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            hClick()
                                            leftIndex = i
                                            selectCategory(i)
                                            focusZone = FocusZone.RIGHT
                                        }
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        val backSelected = (leftIndex == backIndexLeft && focusZone == FocusZone.LEFT)
                        val backBg = if (backSelected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = backBg),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    hClick()
                                    onBackToHome()
                                }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = stringResource(R.string.common_back),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                // RIGHT
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = rightListState,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        userScrollEnabled = true
                    ) {
                        itemsIndexed(rowsForCategory) { i, row ->
                            val selected = (i == rowIndex && focusZone == FocusZone.RIGHT)
                            val expanded = (expandedRowIndex == i)

                            SettingRowCard(
                                context = context,
                                vibrationEnabled = vibrationEnabled,
                                row = row,
                                selected = selected,
                                expanded = expanded,
                                expandedOptionIndex = expandedOptionIndex,
                                onClickRow = {
                                    hClick()
                                    focusZone = FocusZone.RIGHT
                                    rowIndex = i

                                    when (row) {
                                        is SettingRow.Action -> scope.launch { row.onClick() }
                                        is SettingRow.Toggle,
                                        is SettingRow.Picker,
                                        is SettingRow.ExpandableSliderRow -> {
                                            if (expanded) collapseExpanded() else expandRowIfPossible(i)
                                        }
                                    }
                                },
                                onExpandableSliderChange = { v ->
                                    if (row is SettingRow.ExpandableSliderRow) {
                                        expandedRowIndex = i
                                        expandedOptionIndex = v.coerceIn(row.min, row.max)
                                        scope.launch { row.set(expandedOptionIndex) }
                                    }
                                },
                                onPickOption = { opt ->
                                    hClick()
                                    expandedRowIndex = i
                                    expandedOptionIndex = opt
                                    commitExpandedSelection(forRowIndex = i, optionIndex = opt)
                                }
                            )

                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRowCard(
    context: Context,
    vibrationEnabled: Boolean,
    row: SettingRow,
    selected: Boolean,
    expanded: Boolean,
    expandedOptionIndex: Int,
    onClickRow: () -> Unit,
    onExpandableSliderChange: (Int) -> Unit,
    onPickOption: (Int) -> Unit
) {
    fun hTick() {
        if (vibrationEnabled) Haptics.tick(context)
    }

    val baseBg = if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f)

    Card(
        colors = CardDefaults.cardColors(containerColor = baseBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClickRow() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    row.subtitle?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }

                when (row) {
                    is SettingRow.Toggle -> {
                        val v = row.get()
                        Text(
                            text = if (v) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    is SettingRow.Picker -> {
                        val label = row.options.getOrNull(row.getIndex()) ?: ""
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    is SettingRow.Action -> {
                        Text(
                            text = "A",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    is SettingRow.ExpandableSliderRow -> {
                        val v = row.get().coerceIn(row.min, row.max)
                        Text(
                            text = v.toString(),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && (row is SettingRow.Toggle || row is SettingRow.Picker || row is SettingRow.ExpandableSliderRow),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(10.dp))

                    when (row) {
                        is SettingRow.Toggle -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OptionButton(
                                    context = context,
                                    vibrationEnabled = vibrationEnabled,
                                    text = stringResource(R.string.common_off),
                                    selected = expandedOptionIndex == 0,
                                    onClick = { onPickOption(0) }
                                )
                                OptionButton(
                                    context = context,
                                    vibrationEnabled = vibrationEnabled,
                                    text = stringResource(R.string.common_on),
                                    selected = expandedOptionIndex == 1,
                                    onClick = { onPickOption(1) }
                                )
                            }
                        }

                        is SettingRow.Picker -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.options.forEachIndexed { idx, label ->
                                    OptionButton(
                                        context = context,
                                        vibrationEnabled = vibrationEnabled,
                                        text = label,
                                        selected = expandedOptionIndex == idx,
                                        onClick = { onPickOption(idx) }
                                    )
                                }
                            }
                        }

                        is SettingRow.ExpandableSliderRow -> {
                            val v = expandedOptionIndex.coerceIn(row.min, row.max)
                            var lastTickValue by remember(row.title) { mutableIntStateOf(v) }
                            LaunchedEffect(v) { lastTickValue = v }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = v.toString(),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(36.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Slider(
                                    value = v.toFloat(),
                                    onValueChange = {
                                        val raw = it.toInt()
                                        val snapped = snapToStep(raw, row.min, row.max, row.step)
                                        if (snapped != lastTickValue) {
                                            lastTickValue = snapped
                                            hTick() // ✅ ütközőnél tick
                                        }
                                        onExpandableSliderChange(snapped)
                                    },
                                    valueRange = row.min.toFloat()..row.max.toFloat(),
                                    steps = (((row.max - row.min) / row.step).coerceAtLeast(1)) - 1,
                                    modifier = Modifier.width(320.dp)
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionButton(
    context: Context,
    vibrationEnabled: Boolean,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    val bg = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                hClick()
                onClick()
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}