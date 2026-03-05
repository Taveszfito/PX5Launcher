package com.dueboysenberry1226.px5launcher.ui

import android.content.res.Configuration
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun CalendarPanelCard(
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    registerKeyHandler: (handler: (KeyEvent) -> Boolean) -> Unit,
    focusRequester: FocusRequester? = null,
    cornerRadius: Dp = 22.dp,
    vibrationEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val hu = remember { Locale.Builder().setLanguage("hu").setRegion("HU").build() }
    val scope = rememberCoroutineScope()

    val isPortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    // ✅ “lapozó mód” (controller ENTER + touch tap) — PORTRAITBAN OFF
    var pagingMode by remember { mutableStateOf(false) }

    val confirmCodes = remember {
        setOf(
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A
        )
    }
    val backCodes = remember {
        setOf(
            AndroidKeyEvent.KEYCODE_BACK,
            AndroidKeyEvent.KEYCODE_ESCAPE,
            AndroidKeyEvent.KEYCODE_BUTTON_B
        )
    }

    val month = remember(today) { YearMonth.from(today) }
    val monthTitle = remember(month) {
        val m = month.month.getDisplayName(TextStyle.FULL, hu)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(hu) else it.toString() }
        "$m ${month.year}"
    }
    val dayBig = remember(today) { today.dayOfMonth.toString() }

    val gridDates = remember(month) { buildMonthGrid6Weeks(month) }

    val scrollState = rememberScrollState()

    // child számolja: 1 sor lépés px-ben + autofollow target
    var rowStepPx by remember { mutableFloatStateOf(220f) }
    var autoFollowTargetPx by remember { mutableIntStateOf(0) }

    // ✅ auto-follow: ha nem pagingMode, animáltan a mai sorhoz
    LaunchedEffect(today, pagingMode, autoFollowTargetPx) {
        if (pagingMode) return@LaunchedEffect
        val target = max(0, autoFollowTargetPx)
        val clamped = min(target, scrollState.maxValue)
        scrollState.animateScrollTo(clamped)
    }

    // ✅ key handler: pagingMode alatt fel/le animált scroll + ENTER toggle
    LaunchedEffect(Unit) {
        registerKeyHandler { e ->
            val nk = e.nativeKeyEvent
            if (nk.action != AndroidKeyEvent.ACTION_DOWN) return@registerKeyHandler false
            val code = nk.keyCode

            // ✅ Portrait: touch-only -> ne legyen "belépés"
            if (isPortrait) return@registerKeyHandler false

            if (code in confirmCodes) {
                hClick()
                pagingMode = !pagingMode
                return@registerKeyHandler true
            }

            if (code in backCodes) {
                if (pagingMode) {
                    hClick()
                    pagingMode = false
                    return@registerKeyHandler true
                }
                return@registerKeyHandler false
            }

            if (!pagingMode) return@registerKeyHandler false

            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    scope.launch { scrollState.animateScrollBy(-rowStepPx) }
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    scope.launch { scrollState.animateScrollBy(rowStepPx) }
                    true
                }
                else -> true
            }
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val borderAlpha by animateFloatAsState(
        targetValue = if (pagingMode) 0.32f else 0.18f,
        label = "calBorderAlpha"
    )

    // =========================
    // ✅ PORTRAIT UI (touch-only)
    // - nincs controller hint
    // - nincs "belépés lapozásba"
    // - nincs nagy nap
    // - nincs év
    // - hónap felül vékony sávban
    // - grid alul ül
    // =========================
    if (isPortrait) {
        val monthNameOnly = remember(month) {
            month.month.getDisplayName(TextStyle.FULL, hu)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(hu) else it.toString() }
        }

        pagingMode = false

        Column(
            modifier = modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .fillMaxSize()
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(2.dp, Color.White.copy(alpha = 0.18f), shape)
                .padding(horizontal = 14.dp, vertical = 6.dp) // ✅ kevesebb felső tér
        ) {
            // ✅ hónap fentebb + kisebb sáv
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = monthNameOnly,
                    color = Color.White.copy(alpha = 0.82f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // ✅ grid alul ül
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.BottomCenter
            ) {
                CalendarGridMonthScrollable(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    dates = gridDates,
                    today = today,
                    currentMonth = YearMonth.from(today),
                    pagingMode = false,
                    setPagingMode = { /* no-op */ },
                    scrollState = scrollState,
                    onRowStepPx = { rowStepPx = it },
                    onAutoFollowTargetPx = { autoFollowTargetPx = it },
                    compact = true,
                    touchScrollOnly = true
                )
            }
        }

        return
    }

    // =========================
    // ✅ LANDSCAPE UI (eredeti)
    // =========================
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxSize()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(2.dp, Color.White.copy(alpha = borderAlpha), shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.26f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayBig,
                color = Color.White.copy(alpha = 0.95f),
                fontWeight = FontWeight.Bold,
                fontSize = 72.sp,
                lineHeight = 72.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = monthTitle,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (pagingMode)
                    stringResource(R.string.calendar_hint_paging_mode)
                else
                    stringResource(R.string.calendar_hint_enter_paging),
                color = Color.White.copy(alpha = 0.45f),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(18.dp))

        CalendarGridMonthScrollable(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.74f),
            dates = gridDates,
            today = today,
            currentMonth = YearMonth.from(today),
            pagingMode = pagingMode,
            setPagingMode = { newValue ->
                if (newValue != pagingMode) hClick()
                pagingMode = newValue
            },
            scrollState = scrollState,
            onRowStepPx = { rowStepPx = it },
            onAutoFollowTargetPx = { autoFollowTargetPx = it },
            compact = false
        )
    }
}

@Composable
private fun CalendarGridMonthScrollable(
    modifier: Modifier,
    dates: List<LocalDate>,
    today: LocalDate,
    currentMonth: YearMonth,
    pagingMode: Boolean,
    setPagingMode: (Boolean) -> Unit,
    scrollState: ScrollState,
    onRowStepPx: (Float) -> Unit,
    onAutoFollowTargetPx: (Int) -> Unit,
    compact: Boolean,
    touchScrollOnly: Boolean = false
) {
    BoxWithConstraints(modifier = modifier) {
        val availW = this@BoxWithConstraints.maxWidth.coerceAtLeast(1.dp)
        val availH = this@BoxWithConstraints.maxHeight.coerceAtLeast(1.dp)

        // ✅ KÖTELEZŐ: 7 oszlop
        val cols = 7
        val rows = 6

        // ✅ kevesebb hézag
        val headerH = if (compact) 16.dp else 22.dp
        val headerGap = if (compact) 6.dp else 12.dp
        val gapX = if (compact) 6.dp else 14.dp
        val gapY = if (compact) 6.dp else 14.dp

        // ✅ FONTOS FIX: ne legyen olyan "minimum cella", ami túlcsordítja a 7 oszlopot.
        // Csak MAX-ot clampelünk, hogy mindig beférjen 7 egy sorba és ne nyomódjon a 7. nap.
        val cellFromW = (availW - gapX * (cols - 1)) / cols
        val cellMax = if (compact) 78.dp else 92.dp
        val cell = cellFromW.coerceAtMost(cellMax)

        val dayCorner = when {
            cell >= 72.dp -> 16.dp
            cell >= 62.dp -> 14.dp
            else -> 12.dp
        }
        val dayShape = RoundedCornerShape(dayCorner)

        val dayFont = when {
            cell >= 72.dp -> 16.sp
            cell >= 62.dp -> 15.sp
            else -> 14.sp
        }

        // ✅ ez innentől garantáltan <= availW
        val gridW = cell * cols + gapX * (cols - 1)
        val fullGridH = cell * rows + gapY * (rows - 1)

        val scrollViewportH = (availH - headerH - headerGap).coerceAtLeast(1.dp)

        val density = LocalDensity.current
        val rowStepPx = remember(cell, gapY) {
            with(density) { (cell + gapY).toPx() }
        }

        LaunchedEffect(rowStepPx) { onRowStepPx(rowStepPx) }

        // ✅ Auto-follow target: a mai sor kb középre (landscape)
        LaunchedEffect(today, cell, scrollViewportH, rowStepPx) {
            val idx = dates.indexOfFirst { it == today }
            if (idx < 0) {
                onAutoFollowTargetPx(0)
                return@LaunchedEffect
            }
            val row = idx / 7

            val cellPx = with(density) { cell.toPx() }
            val viewportPx = with(density) { scrollViewportH.toPx() }

            val rowTop = (row * rowStepPx)
            val target = (rowTop - (viewportPx / 2f) + (cellPx / 2f)).toInt()

            onAutoFollowTargetPx(max(0, target))
        }

        val tapSlopPx = remember { with(density) { 10.dp.toPx() } }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            // ✅ Weekday header: ugyanakkora cellák, ugyanazzal a gapX-szel -> pixelpontos igazítás
            Row(
                modifier = Modifier
                    .width(gridW)
                    .align(Alignment.CenterHorizontally)
                    .height(headerH),
                horizontalArrangement = Arrangement.spacedBy(gapX),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val weekdays = listOf(
                    stringResource(R.string.calendar_weekday_mon),
                    stringResource(R.string.calendar_weekday_tue),
                    stringResource(R.string.calendar_weekday_wed),
                    stringResource(R.string.calendar_weekday_thu),
                    stringResource(R.string.calendar_weekday_fri),
                    stringResource(R.string.calendar_weekday_sat),
                    stringResource(R.string.calendar_weekday_sun)
                )

                weekdays.forEach { label ->
                    Box(
                        modifier = Modifier.width(cell),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            textAlign = TextAlign.Center,
                            color = Color.White.copy(alpha = 0.65f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (compact) 11.sp else 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(headerGap))

            Box(
                modifier = Modifier
                    .width(gridW)
                    .height(scrollViewportH)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(pagingMode, touchScrollOnly) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)

                            var totalDx = 0f
                            var totalDy = 0f
                            var isDragging = false

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val ch = event.changes.firstOrNull() ?: break
                                if (!ch.pressed) break

                                val delta = ch.positionChange()
                                if (delta.x != 0f || delta.y != 0f) {
                                    totalDx += delta.x
                                    totalDy += delta.y
                                }

                                val dist = abs(totalDx) + abs(totalDy)

                                // ✅ Portrait: mindig drag-to-scroll
                                if (touchScrollOnly) {
                                    if (!isDragging && dist > tapSlopPx) isDragging = true
                                    if (isDragging) {
                                        ch.consume()
                                        val scrollDelta = -delta.y
                                        if (scrollDelta != 0f) scrollState.dispatchRawDelta(scrollDelta)
                                    }
                                    continue
                                }

                                // ✅ Landscape: pagingMode ON alatt drag scroll + consume
                                if (!pagingMode) {
                                    if (dist > tapSlopPx) return@awaitEachGesture
                                } else {
                                    if (!isDragging && dist > tapSlopPx) isDragging = true
                                    if (isDragging) {
                                        ch.consume()
                                        val scrollDelta = -delta.y
                                        if (scrollDelta != 0f) scrollState.dispatchRawDelta(scrollDelta)
                                    }
                                }
                            }

                            // Tap: csak landscape toggle
                            val wasTap = (abs(totalDx) + abs(totalDy)) <= tapSlopPx
                            if (wasTap && !touchScrollOnly) {
                                setPagingMode(!pagingMode)
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .height(fullGridH)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(gapY)
                ) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gapX)
                        ) {
                            for (c in 0 until cols) {
                                val idx = r * cols + c
                                val d = dates[idx]
                                val isToday = d == today

                                val daysDiff = d.toEpochDay() - today.toEpochDay()
                                val pastDistance = if (daysDiff < 0) abs(daysDiff).toInt() else 0
                                val baseAlpha = if (daysDiff < 0) {
                                    val t = (pastDistance / 5f).coerceIn(0f, 1f)
                                    (0.85f - t * 0.55f).coerceAtLeast(0.28f)
                                } else 0.85f

                                val isOtherMonth = YearMonth.from(d) != currentMonth
                                val otherMonthMul = if (isOtherMonth) 0.55f else 1.0f
                                val alpha = (baseAlpha * otherMonthMul).coerceAtLeast(0.22f)

                                val bgAlpha = if (isToday) 0.20f else if (pagingMode) 0.08f else 0.06f
                                val borderAlpha = if (isToday) 0.95f else if (pagingMode) 0.20f else 0.14f

                                Box(
                                    modifier = Modifier
                                        .size(cell)
                                        .alpha(alpha)
                                        .clip(dayShape)
                                        .background(Color.White.copy(alpha = bgAlpha))
                                        .border(2.dp, Color.White.copy(alpha = borderAlpha), dayShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = d.dayOfMonth.toString(),
                                        color = Color.White.copy(alpha = if (isToday) 0.95f else 0.88f),
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = dayFont
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildMonthGrid6Weeks(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val start = mondayOfWeek(first)
    return List(42) { i -> start.plusDays(i.toLong()) }
}

private fun mondayOfWeek(d: LocalDate): LocalDate {
    val dow = d.dayOfWeek.value // MON=1.SUN=7
    return d.minusDays((dow - DayOfWeek.MONDAY.value).toLong())
}