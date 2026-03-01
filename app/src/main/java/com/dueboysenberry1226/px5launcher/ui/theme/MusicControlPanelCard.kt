@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Shader
import android.graphics.RenderEffect as AndroidRenderEffect
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.media.PX5NotificationListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class QueueItemUi(
    val queueId: Long,
    val title: String
)

@Composable
fun MusicControlPanelCard(
    modifier: Modifier = Modifier,
    registerKeyHandler: (handler: (KeyEvent) -> Boolean) -> Unit,
    focusRequester: FocusRequester? = null,
    cornerRadius: Dp = 22.dp,
    vibrationEnabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var hasPermissionIssue by remember { mutableStateOf(false) }

    var selected by remember { mutableStateOf(Selected.SEEK) }
    var lastControlSelected by remember { mutableStateOf(Selected.PLAY_PAUSE) }

    var title by remember { mutableStateOf(context.getString(R.string.music_no_active_playback)) }
    var artist by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }

    var queueItems by remember { mutableStateOf<List<QueueItemUi>>(emptyList()) }
    var canSkipToQueueItem by remember { mutableStateOf(false) }
    var listIndex by remember { mutableStateOf(0) }
    var listHint by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    var seekMode by remember { mutableStateOf(false) }

    var lastTrackKey by remember { mutableStateOf("") }

    var autoSkipActive by remember { mutableStateOf(false) }
    var autoSkipTargetTitleNorm by remember { mutableStateOf("") }

    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volNow by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val volMax = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var autoSkipSavedVolume by remember { mutableStateOf<Int?>(null) }

    val noRipple = remember { MutableInteractionSource() }

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }
    fun hTick() {
        if (vibrationEnabled) Haptics.tick(context)
    }

    fun refreshVolume() {
        volNow = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun adjustVolume(delta: Int) {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0
        )
        refreshVolume()
    }

    fun hasNotificationListenerAccess(ctx: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }

    fun openNotificationListenerSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun tryAttachController() {
        controller = null

        if (!hasNotificationListenerAccess(context)) {
            hasPermissionIssue = true
            return
        }
        hasPermissionIssue = false

        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            val sessions = msm.getActiveSessions(
                ComponentName(context, PX5NotificationListener::class.java)
            )
            val best =
                sessions.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
                    ?: sessions.firstOrNull()

            controller = best
        } catch (_: SecurityException) {
            hasPermissionIssue = true
            controller = null
        } catch (_: Throwable) {
            controller = null
        }
    }

    fun normalizeTitle(s: String): String {
        return s
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace("–", "-")
            .replace("—", "-")
            .trim()
    }

    fun indexOfTitleInQueue(currentTitle: String, items: List<QueueItemUi>): Int {
        val curNorm = normalizeTitle(currentTitle)
        if (curNorm.isBlank() || items.isEmpty()) return -1
        return items.indexOfFirst { qi ->
            val qNorm = normalizeTitle(qi.title)
            qNorm == curNorm || qNorm.contains(curNorm) || curNorm.contains(qNorm)
        }
    }

    fun clampListIndex() {
        if (queueItems.isEmpty()) {
            listIndex = 0
            if (selected == Selected.LIST) selected = Selected.SEEK
            return
        }
        listIndex = listIndex.coerceIn(0, queueItems.lastIndex)
    }

    fun pullFromController(c: MediaController?) {
        if (c == null) {
            title = if (hasPermissionIssue) {
                context.getString(R.string.music_grant_notification_access)
            } else {
                context.getString(R.string.music_no_active_playback)
            }

            artist = if (hasPermissionIssue) {
                context.getString(R.string.music_open_settings_hint)
            } else {
                ""
            }

            isPlaying = false
            durationMs = 0L
            positionMs = 0L
            queueItems = emptyList()
            canSkipToQueueItem = false
            listIndex = 0
            autoSkipActive = false
            return
        }

        val md = c.metadata
        val desc = md?.description
        val controllerTitle = desc?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.music_unknown_media)
        val controllerArtist = desc?.subtitle?.toString().orEmpty()

        val st = c.playbackState
        val playingNow = st?.state == android.media.session.PlaybackState.STATE_PLAYING
        var posNow = st?.position ?: 0L
        var durNow = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (durNow < 0) durNow = 0L
        if (posNow < 0) posNow = 0L
        if (durNow > 0) posNow = posNow.coerceIn(0L, durNow)

        val actions = st?.actions ?: 0L
        canSkipToQueueItem =
            (actions and android.media.session.PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM) != 0L

        val rawQueue = c.queue
            ?.mapNotNull { qi ->
                val qTitle = qi.description?.title?.toString()?.trim().orEmpty()
                if (qTitle.isBlank()) null else QueueItemUi(queueId = qi.queueId, title = qTitle)
            }
            .orEmpty()

        if (autoSkipActive) {
            isPlaying = playingNow
            positionMs = posNow
            durationMs = durNow

            val nowNorm = normalizeTitle(controllerTitle)
            val reached = autoSkipTargetTitleNorm.isNotBlank() && (
                    nowNorm == autoSkipTargetTitleNorm ||
                            nowNorm.contains(autoSkipTargetTitleNorm) ||
                            autoSkipTargetTitleNorm.contains(nowNorm)
                    )

            queueItems = rawQueue
            clampListIndex()

            if (reached) {
                autoSkipActive = false
                title = controllerTitle
                artist = controllerArtist
            }
            return
        }

        title = controllerTitle
        artist = controllerArtist
        isPlaying = playingNow
        positionMs = posNow
        durationMs = durNow

        val curIdxInRaw = indexOfTitleInQueue(controllerTitle, rawQueue)
        val uiQueue =
            if (rawQueue.isNotEmpty() && curIdxInRaw < 0) {
                val curNorm = normalizeTitle(controllerTitle)
                val filtered = rawQueue.filter { normalizeTitle(it.title) != curNorm }
                val prefixedNowPlaying =
                    context.getString(R.string.music_now_playing_prefix, controllerTitle)
                listOf(QueueItemUi(queueId = -1L, title = prefixedNowPlaying)) + filtered
            } else rawQueue

        queueItems = uiQueue
        clampListIndex()

        val trackKeyNow = normalizeTitle(controllerTitle) + "|" + normalizeTitle(controllerArtist)
        val trackChanged = trackKeyNow.isNotBlank() && trackKeyNow != lastTrackKey

        if (trackChanged && queueItems.isNotEmpty()) {
            val rawPrefix = context.getString(R.string.music_now_playing_prefix_raw)
            val isFallback = queueItems.firstOrNull()?.title?.startsWith(rawPrefix) == true
            if (isFallback) {
                listIndex = if (queueItems.size >= 2) 1 else 0
            } else {
                val idx = indexOfTitleInQueue(controllerTitle, queueItems)
                if (idx >= 0) listIndex = (idx + 1).coerceIn(0, queueItems.lastIndex)
            }
            lastTrackKey = trackKeyNow
        }
    }

    LaunchedEffect(Unit) {
        tryAttachController()
        pullFromController(controller)
        refreshVolume()

        while (true) {
            if (controller == null) tryAttachController()
            pullFromController(controller)
            refreshVolume()
            delay(500)
        }
    }

    LaunchedEffect(listHint) {
        if (listHint != null) {
            delay(1200)
            listHint = null
        }
    }

    LaunchedEffect(selected, listIndex, queueItems.size) {
        if (selected == Selected.LIST && queueItems.isNotEmpty()) {
            val target = listIndex.coerceIn(0, queueItems.lastIndex)
            listState.animateScrollToItem(target)
        }
    }

    fun openQueueIfAny(): Boolean {
        if (queueItems.isEmpty()) return false
        selected = Selected.LIST
        seekMode = false
        clampListIndex()
        return true
    }

    fun playQueueItemOrAutoSkip(index: Int): Boolean {
        if (queueItems.isEmpty()) return false
        val c = controller ?: return false

        val safeIndex = index.coerceIn(0, queueItems.lastIndex)
        val item = queueItems[safeIndex]
        if (item.queueId == -1L) {
            listHint = context.getString(R.string.music_list_hint_this_is_current)
            return true
        }

        val st = c.playbackState
        val canDirect = st != null &&
                (st.actions and android.media.session.PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM) != 0L

        if (canDirect) {
            c.transportControls.skipToQueueItem(item.queueId)
            return true
        }

        val curIdx = indexOfTitleInQueue(title, queueItems)
        if (curIdx < 0) {
            listHint = context.getString(R.string.music_list_hint_cant_find_current)
            return true
        }

        val delta = safeIndex - curIdx
        if (delta == 0) {
            listHint = context.getString(R.string.music_list_hint_already_playing)
            return true
        }

        val steps = abs(delta).coerceAtMost(30)
        val directionNext = delta > 0

        autoSkipActive = true
        autoSkipTargetTitleNorm = normalizeTitle(queueItems[safeIndex].title)
        listHint = context.getString(R.string.music_list_hint_skipping, steps)
        seekMode = false

        if (autoSkipSavedVolume == null) {
            autoSkipSavedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        refreshVolume()

        scope.launch {
            repeat(steps) {
                if (!autoSkipActive) return@launch
                if (directionNext) c.transportControls.skipToNext() else c.transportControls.skipToPrevious()
                delay(140)
            }

            var wait = 0
            while (autoSkipActive && wait < 14) {
                delay(120)
                wait++
            }

            if (autoSkipActive) {
                autoSkipActive = false
                autoSkipTargetTitleNorm = ""
                listHint = context.getString(R.string.music_list_hint_maybe_not_reached)
            }

            autoSkipSavedVolume?.let {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
            }
            autoSkipSavedVolume = null
            refreshVolume()
        }

        return true
    }

    LaunchedEffect(Unit) {
        registerKeyHandler { e ->
            val nk = e.nativeKeyEvent
            if (nk.action != AndroidKeyEvent.ACTION_DOWN) return@registerKeyHandler false
            val code = nk.keyCode

            val confirmCodes = setOf(
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                AndroidKeyEvent.KEYCODE_BUTTON_A
            )
            val backCodes = setOf(
                AndroidKeyEvent.KEYCODE_BACK,
                AndroidKeyEvent.KEYCODE_ESCAPE,
                AndroidKeyEvent.KEYCODE_BUTTON_B
            )

            if (code in backCodes) {
                if (seekMode) {
                    seekMode = false
                    return@registerKeyHandler true
                }
                if (autoSkipActive) {
                    autoSkipActive = false
                    autoSkipTargetTitleNorm = ""
                    listHint = context.getString(R.string.music_list_hint_cancelled)

                    autoSkipSavedVolume?.let {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
                    }
                    autoSkipSavedVolume = null
                    refreshVolume()

                    return@registerKeyHandler true
                }
                return@registerKeyHandler false
            }

            if (code in confirmCodes && hasPermissionIssue) {
                openNotificationListenerSettings(context)
                return@registerKeyHandler true
            }

            if (code in confirmCodes) {
                when (selected) {
                    Selected.PREV -> {
                        controller?.transportControls?.skipToPrevious()
                        return@registerKeyHandler true
                    }
                    Selected.PLAY_PAUSE -> {
                        val c = controller
                        if (c != null) {
                            if (isPlaying) c.transportControls.pause() else c.transportControls.play()
                        } else {
                            tryAttachController()
                        }
                        return@registerKeyHandler true
                    }
                    Selected.NEXT -> {
                        controller?.transportControls?.skipToNext()
                        return@registerKeyHandler true
                    }
                    Selected.VOL_DOWN -> {
                        adjustVolume(-1)
                        return@registerKeyHandler true
                    }
                    Selected.VOL_UP -> {
                        adjustVolume(+1)
                        return@registerKeyHandler true
                    }
                    Selected.SEEK -> {
                        seekMode = !seekMode
                        return@registerKeyHandler true
                    }
                    Selected.LIST -> {
                        return@registerKeyHandler playQueueItemOrAutoSkip(listIndex)
                    }
                }
            }

            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    when (selected) {
                        Selected.LIST -> {
                            selected = Selected.SEEK
                            true
                        }
                        Selected.SEEK -> {
                            if (seekMode) {
                                doSeekBy(-5000L, controller, durationMs, positionMs) { positionMs = it }
                                true
                            } else false
                        }
                        else -> {
                            selected = selected.prevControl()
                            lastControlSelected = selected
                            true
                        }
                    }
                }

                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    when (selected) {
                        Selected.LIST -> false
                        Selected.SEEK -> {
                            if (seekMode) {
                                doSeekBy(+5000L, controller, durationMs, positionMs) { positionMs = it }
                                true
                            } else {
                                openQueueIfAny()
                            }
                        }
                        Selected.VOL_UP -> openQueueIfAny()
                        else -> {
                            selected = selected.nextControl()
                            lastControlSelected = selected
                            true
                        }
                    }
                }

                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    when (selected) {
                        Selected.LIST -> {
                            if (queueItems.isEmpty()) return@registerKeyHandler false
                            if (listIndex < queueItems.lastIndex) {
                                listIndex++
                                true
                            } else false
                        }
                        else -> {
                            if (selected != Selected.SEEK) {
                                selected = Selected.SEEK
                                true
                            } else false
                        }
                    }
                }

                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    when (selected) {
                        Selected.LIST -> {
                            if (queueItems.isEmpty()) return@registerKeyHandler false
                            if (listIndex > 0) {
                                listIndex--
                                true
                            } else {
                                selected = Selected.SEEK
                                true
                            }
                        }
                        Selected.SEEK -> {
                            if (!seekMode) {
                                selected = lastControlSelected
                                true
                            } else false
                        }
                        else -> false
                    }
                }

                else -> false
            }
        }
    }

    val shape = RoundedCornerShape(cornerRadius)
    val borderAlpha by animateFloatAsState(
        targetValue = if (seekMode || autoSkipActive) 0.32f else 0.18f,
        label = "musicBorderAlpha"
    )

    val showQueue = queueItems.isNotEmpty()

    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxSize()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(2.dp, Color.White.copy(alpha = borderAlpha), shape)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(if (showQueue) 2f else 1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = when {
                    hasPermissionIssue -> stringResource(R.string.music_hint_enable_permission)
                    autoSkipActive -> stringResource(R.string.music_hint_skipping_cancel)
                    seekMode -> stringResource(R.string.music_hint_seek_mode)
                    showQueue && !canSkipToQueueItem -> stringResource(R.string.music_hint_queue_autoskip)
                    showQueue -> stringResource(R.string.music_hint_queue_next_songs)
                    else -> stringResource(R.string.music_hint_strip_seek_mode)
                },
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (hasPermissionIssue) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            interactionSource = noRipple,
                            indication = null,
                            onClick = {
                                hClick()
                                openNotificationListenerSettings(context)
                            }
                        )
                        .padding(6.dp)
                } else Modifier
            )

            if (!hasPermissionIssue && artist.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✅ egységes ikonok (nem emoji)
                ControlChip(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    selected = selected == Selected.PREV,
                    onClick = {
                        hClick()
                        if (hasPermissionIssue) openNotificationListenerSettings(context)
                        else controller?.transportControls?.skipToPrevious()
                    }
                )

                ControlChip(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    selected = selected == Selected.PLAY_PAUSE,
                    onClick = {
                        hClick()
                        if (hasPermissionIssue) openNotificationListenerSettings(context)
                        else {
                            val c = controller
                            if (c != null) {
                                if (isPlaying) c.transportControls.pause() else c.transportControls.play()
                            } else tryAttachController()
                        }
                    }
                )

                ControlChip(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    selected = selected == Selected.NEXT,
                    onClick = {
                        hClick()
                        if (hasPermissionIssue) openNotificationListenerSettings(context)
                        else controller?.transportControls?.skipToNext()
                    }
                )

                Spacer(Modifier.weight(1f))

                // marad szöveges, ez nem emoji
                ControlChip(
                    label = "VOL−",
                    selected = selected == Selected.VOL_DOWN,
                    onClick = {
                        hTick()
                        adjustVolume(-1)
                    }
                )

                Text(
                    text = stringResource(R.string.music_volume_format, volNow, volMax),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                ControlChip(
                    label = "VOL+",
                    selected = selected == Selected.VOL_UP,
                    onClick = {
                        hTick()
                        adjustVolume(+1)
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            SeekBar(
                modifier = Modifier.fillMaxWidth(),
                selected = selected == Selected.SEEK,
                seekMode = seekMode,
                hasPermissionIssue = hasPermissionIssue,
                positionMs = positionMs,
                durationMs = durationMs,
                onRequestPermission = {
                    hClick()
                    openNotificationListenerSettings(context)
                },
                onSeekToFraction = { frac ->
                    hTick()
                    val c = controller ?: return@SeekBar
                    val d = durationMs
                    if (d <= 0L) return@SeekBar
                    val target = (d * frac).toLong().coerceIn(0L, d)
                    c.transportControls.seekTo(target)
                    positionMs = target
                },
                onToggleSeekMode = {
                    hClick()
                    seekMode = !seekMode
                }
            )

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Text(
                    text = formatMs(context, positionMs),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (durationMs > 0L) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "• " + formatMs(context, durationMs),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (showQueue) {
            QueuePanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                selected = selected == Selected.LIST,
                busy = autoSkipActive,
                items = queueItems,
                index = listIndex,
                hint = listHint,
                canJump = canSkipToQueueItem,
                listState = listState,
                onClickItem = { i ->
                    hClick()
                    selected = Selected.LIST
                    listIndex = i.coerceIn(0, queueItems.lastIndex)
                    playQueueItemOrAutoSkip(listIndex)
                }
            )
        }
    }
}

private enum class Selected {
    PREV, PLAY_PAUSE, NEXT, VOL_DOWN, VOL_UP, SEEK, LIST;

    fun nextControl(): Selected {
        val controls = listOf(PREV, PLAY_PAUSE, NEXT, VOL_DOWN, VOL_UP)
        val i = controls.indexOf(this).takeIf { it >= 0 } ?: 0
        return controls[(i + 1) % controls.size]
    }

    fun prevControl(): Selected {
        val controls = listOf(PREV, PLAY_PAUSE, NEXT, VOL_DOWN, VOL_UP)
        val i = controls.indexOf(this).takeIf { it >= 0 } ?: 0
        return controls[(i - 1 + controls.size) % controls.size]
    }
}

@Composable
private fun ControlChip(
    label: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val a = if (selected) 0.22f else 0.10f
    val b = if (selected) 0.55f else 0.18f
    val noRipple = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = a))
            .border(2.dp, Color.White.copy(alpha = b), shape)
            .combinedClickable(
                interactionSource = noRipple,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(18.dp)
                )
            }
            !label.isNullOrBlank() -> {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun Modifier.queueBlurIf(active: Boolean): Modifier {
    if (!active) return this
    return if (Build.VERSION.SDK_INT >= 31) {
        this.graphicsLayer {
            renderEffect = AndroidRenderEffect
                .createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        this
    }
}

@Composable
private fun QueuePanel(
    modifier: Modifier,
    selected: Boolean,
    busy: Boolean,
    items: List<QueueItemUi>,
    index: Int,
    hint: String?,
    canJump: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onClickItem: (Int) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val borderA = if (selected) 0.55f else 0.18f
    val bgA = if (selected) 0.10f else 0.06f
    val noRipple = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = bgA))
            .border(2.dp, Color.White.copy(alpha = borderA), shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .queueBlurIf(busy)
                .then(if (busy) Modifier.background(Color.Black.copy(alpha = 0.12f)) else Modifier)
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.music_queue_title),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = hint ?: if (canJump) stringResource(R.string.music_queue_hint_play) else stringResource(R.string.music_queue_hint_autoskip),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items) { i, item ->
                    val rowSelected = selected && i == index

                    val rowShape = RoundedCornerShape(14.dp)
                    val rowBgA = if (rowSelected) 0.22f else 0.06f
                    val rowBorderA = if (rowSelected) 0.55f else 0.12f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(Color.White.copy(alpha = rowBgA))
                            .border(2.dp, Color.White.copy(alpha = rowBorderA), rowShape)
                            .combinedClickable(
                                interactionSource = noRipple,
                                indication = null,
                                enabled = !busy,
                                onClick = { onClickItem(i) }
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // ✅ egységes ikon (nem emoji)
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Skipping",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.music_busy_title),
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.music_busy_cancel),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekBar(
    modifier: Modifier,
    selected: Boolean,
    seekMode: Boolean,
    hasPermissionIssue: Boolean,
    positionMs: Long,
    durationMs: Long,
    onRequestPermission: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onToggleSeekMode: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val borderA = if (selected) 0.55f else 0.18f
    val bgA = if (selected) 0.10f else 0.06f

    val frac = remember(positionMs, durationMs) {
        if (durationMs <= 0L) 0f
        else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = bgA))
            .border(2.dp, Color.White.copy(alpha = borderA), shape)
            .pointerInput(hasPermissionIssue, durationMs, selected) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    if (hasPermissionIssue) {
                        onRequestPermission()
                        return@awaitEachGesture
                    }

                    if (durationMs <= 0L) {
                        if (selected) onToggleSeekMode()
                        return@awaitEachGesture
                    }

                    val w = size.width.coerceAtLeast(1)
                    val f = (down.position.x.coerceIn(0f, w.toFloat())) / w.toFloat()
                    onSeekToFraction(f)
                }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.14f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(frac)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.55f))
        )

        Text(
            text = when {
                hasPermissionIssue -> stringResource(R.string.music_seek_perm_needed)
                durationMs <= 0L -> stringResource(R.string.music_seek_no_data)
                seekMode && selected -> stringResource(R.string.music_seek_mode_step)
                selected -> stringResource(R.string.music_seek_selected_hint)
                else -> ""
            },
            modifier = Modifier.align(Alignment.Center),
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun doSeekBy(
    deltaMs: Long,
    controller: MediaController?,
    durationMs: Long,
    currentPos: Long,
    updatePos: (Long) -> Unit
) {
    val c = controller ?: return
    val d = durationMs
    if (d <= 0L) return

    val target = (currentPos + deltaMs).coerceIn(0L, d)
    c.transportControls.seekTo(target)
    updatePos(target)
}

private fun formatMs(context: Context, ms: Long): String {
    if (ms <= 0L) return context.getString(R.string.music_time_zero)
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "${m}:${s.toString().padStart(2, '0')}"
}