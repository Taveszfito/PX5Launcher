@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class VideoControlButtonId {
    BACK,
    PREV_VIDEO,
    PLAY_PAUSE,
    NEXT_VIDEO,
    VOL_DOWN,
    VOL_UP
}

private fun formatVideoTime(currentMs: Long, durationMs: Long): String {
    fun formatSingle(ms: Long, forceHours: Boolean): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (forceHours || hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    val needsHours = currentMs >= 3_600_000L || durationMs >= 3_600_000L
    return "${formatSingle(currentMs, needsHours)} / ${formatSingle(durationMs, needsHours)}"
}

@Composable
fun MediaVideoPlayer(
    videoUri: Uri,
    videoName: String?,
    modifier: Modifier = Modifier,
    startPlaying: Boolean = true,
    vibrationEnabled: Boolean = false,
    onBack: (() -> Unit)? = null,
    onPreviousVideo: (() -> Unit)? = null,
    onNextVideo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    fun findActivity(ctx: Context): Activity? = when (ctx) {
        is Activity -> ctx
        is ContextWrapper -> findActivity(ctx.baseContext)
        else -> null
    }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = startPlaying
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun getVolumeMax(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    fun getVolumeCurrent(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
    }

    var controlsVisible by rememberSaveable(videoUri.toString()) { mutableStateOf(true) }
    var isPlaying by rememberSaveable(videoUri.toString()) { mutableStateOf(startPlaying) }
    var isScrubbing by rememberSaveable(videoUri.toString()) { mutableStateOf(false) }
    var scrubPositionMs by rememberSaveable(videoUri.toString()) { mutableLongStateOf(0L) }
    var positionMs by rememberSaveable(videoUri.toString()) { mutableLongStateOf(0L) }
    var durationMs by rememberSaveable(videoUri.toString()) { mutableLongStateOf(1L) }
    var currentVolume by rememberSaveable(videoUri.toString()) { mutableIntStateOf(getVolumeCurrent()) }
    var maxVolume by rememberSaveable(videoUri.toString()) { mutableIntStateOf(getVolumeMax()) }
    var interactionTick by rememberSaveable(videoUri.toString()) { mutableIntStateOf(0) }
    var touchMode by rememberSaveable(videoUri.toString()) { mutableStateOf(false) }

    fun refreshVolumeState() {
        currentVolume = getVolumeCurrent()
        maxVolume = getVolumeMax()
    }

    fun pingControls() {
        controlsVisible = true
        interactionTick++
    }

    fun handleTouchInteraction() {
        pingControls()
        if (!touchMode) {
            touchMode = true
            focusManager.clearFocus(force = true)
        }
    }

    fun handleControllerInteraction() {
        if (touchMode) {
            touchMode = false
        }
        pingControls()
    }

    var focusedButton by rememberSaveable(videoUri.toString()) {
        mutableStateOf(VideoControlButtonId.PLAY_PAUSE)
    }
    var focusedIndex by rememberSaveable(videoUri.toString()) { mutableIntStateOf(2) }

    val rootFocusRequester = remember { FocusRequester() }
    val backFR = remember { FocusRequester() }
    val prevFR = remember { FocusRequester() }
    val playPauseFR = remember { FocusRequester() }
    val nextFR = remember { FocusRequester() }
    val volDownFR = remember { FocusRequester() }
    val volUpFR = remember { FocusRequester() }

    val orderedButtons = remember {
        listOf(
            VideoControlButtonId.BACK,
            VideoControlButtonId.PREV_VIDEO,
            VideoControlButtonId.PLAY_PAUSE,
            VideoControlButtonId.NEXT_VIDEO,
            VideoControlButtonId.VOL_DOWN,
            VideoControlButtonId.VOL_UP
        )
    }

    fun requestFocusedButton(index: Int) {
        if (touchMode) return

        focusedIndex = index.coerceIn(0, orderedButtons.lastIndex)
        focusedButton = orderedButtons[focusedIndex]
        when (focusedButton) {
            VideoControlButtonId.BACK -> backFR.requestFocus()
            VideoControlButtonId.PREV_VIDEO -> prevFR.requestFocus()
            VideoControlButtonId.PLAY_PAUSE -> playPauseFR.requestFocus()
            VideoControlButtonId.NEXT_VIDEO -> nextFR.requestFocus()
            VideoControlButtonId.VOL_DOWN -> volDownFR.requestFocus()
            VideoControlButtonId.VOL_UP -> volUpFR.requestFocus()
        }
    }

    fun togglePlayPause() {
        hClick()
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        pingControls()
    }

    fun adjustVolume(direction: Int) {
        hClick()
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        refreshVolumeState()
        pingControls()
    }

    fun invokeFocusedButton() {
        when (focusedButton) {
            VideoControlButtonId.BACK -> {
                hClick()
                onBack?.invoke()
            }

            VideoControlButtonId.PREV_VIDEO -> {
                hClick()
                onPreviousVideo?.invoke()
                pingControls()
            }

            VideoControlButtonId.PLAY_PAUSE -> togglePlayPause()

            VideoControlButtonId.NEXT_VIDEO -> {
                hClick()
                onNextVideo?.invoke()
                pingControls()
            }

            VideoControlButtonId.VOL_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            VideoControlButtonId.VOL_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: 1L
            durationMs = dur
            if (!isScrubbing) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            isPlaying = exoPlayer.isPlaying
            refreshVolumeState()
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isScrubbing, videoUri, interactionTick) {
        if (controlsVisible && isPlaying && !isScrubbing) {
            delay(5000)
            controlsVisible = false
        }
    }

    LaunchedEffect(videoUri) {
        controlsVisible = true
        delay(120)
        rootFocusRequester.requestFocus()
        delay(80)
        if (!touchMode) {
            requestFocusedButton(2)
        }
    }

    LaunchedEffect(videoUri, controlsVisible) {
        val activity = findActivity(context)
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }

        if (window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        handleTouchInteraction()
                    }
                }
                false
            }
            .onPreviewKeyEvent { event: KeyEvent ->
                val native = event.nativeKeyEvent
                if (native.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                handleControllerInteraction()

                when (native.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else {
                            when (native.keyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                    requestFocusedButton((focusedIndex - 1).coerceAtLeast(0))
                                    true
                                }

                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    requestFocusedButton((focusedIndex + 1).coerceAtMost(orderedButtons.lastIndex))
                                    true
                                }

                                AndroidKeyEvent.KEYCODE_DPAD_UP,
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                    true
                                }

                                else -> false
                            }
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                        invokeFocusedButton()
                        true
                    }

                    AndroidKeyEvent.KEYCODE_BUTTON_L1 -> {
                        hClick()
                        onPreviousVideo?.invoke()
                        pingControls()
                        true
                    }

                    AndroidKeyEvent.KEYCODE_BUTTON_R1 -> {
                        hClick()
                        onNextVideo?.invoke()
                        pingControls()
                        true
                    }

                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                        hClick()
                        onBack?.invoke()
                        true
                    }

                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    handleTouchInteraction()
                },
                onLongClick = {
                    handleTouchInteraction()
                    hClick()
                    onBack?.invoke()
                }
            )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 18.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Text(
                    text = videoName ?: "Videó",
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xAA111111)
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.width(0.dp).weight(1f)
                        ) {
                            Slider(
                                value = if (durationMs > 0L) {
                                    val base = if (isScrubbing) scrubPositionMs else positionMs
                                    (base.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                },
                                onValueChange = { fraction ->
                                    handleTouchInteraction()
                                    isScrubbing = true
                                    scrubPositionMs =
                                        (durationMs * fraction).toLong().coerceIn(0L, durationMs)
                                },
                                onValueChangeFinished = {
                                    handleTouchInteraction()
                                    val target = scrubPositionMs.coerceIn(0L, durationMs)
                                    exoPlayer.seekTo(target)
                                    positionMs = target
                                    isScrubbing = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            text = formatVideoTime(
                                currentMs = if (isScrubbing) scrubPositionMs else positionMs,
                                durationMs = durationMs
                            ),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 16.sp
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoGlassButton(
                                icon = Icons.Rounded.ArrowBack,
                                selected = !touchMode && focusedButton == VideoControlButtonId.BACK,
                                focusEnabled = !touchMode,
                                focusRequester = backFR,
                                onFocused = {
                                    focusedButton = VideoControlButtonId.BACK
                                    focusedIndex = 0
                                },
                                onClick = {
                                    handleTouchInteraction()
                                    hClick()
                                    onBack?.invoke()
                                }
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$currentVolume/$maxVolume",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 16.sp
                                )

                                VideoGlassButton(
                                    icon = Icons.Rounded.VolumeDown,
                                    selected = !touchMode && focusedButton == VideoControlButtonId.VOL_DOWN,
                                    focusEnabled = !touchMode,
                                    focusRequester = volDownFR,
                                    onFocused = {
                                        focusedButton = VideoControlButtonId.VOL_DOWN
                                        focusedIndex = 4
                                    },
                                    onClick = {
                                        handleTouchInteraction()
                                        adjustVolume(AudioManager.ADJUST_LOWER)
                                    }
                                )

                                VideoGlassButton(
                                    icon = Icons.Rounded.VolumeUp,
                                    selected = !touchMode && focusedButton == VideoControlButtonId.VOL_UP,
                                    focusEnabled = !touchMode,
                                    focusRequester = volUpFR,
                                    onFocused = {
                                        focusedButton = VideoControlButtonId.VOL_UP
                                        focusedIndex = 5
                                    },
                                    onClick = {
                                        handleTouchInteraction()
                                        adjustVolume(AudioManager.ADJUST_RAISE)
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoGlassButton(
                                icon = Icons.Rounded.FastRewind,
                                selected = !touchMode && focusedButton == VideoControlButtonId.PREV_VIDEO,
                                focusEnabled = !touchMode,
                                focusRequester = prevFR,
                                onFocused = {
                                    focusedButton = VideoControlButtonId.PREV_VIDEO
                                    focusedIndex = 1
                                },
                                onClick = {
                                    handleTouchInteraction()
                                    hClick()
                                    onPreviousVideo?.invoke()
                                    pingControls()
                                }
                            )

                            VideoGlassButton(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                selected = !touchMode && focusedButton == VideoControlButtonId.PLAY_PAUSE,
                                focusEnabled = !touchMode,
                                focusRequester = playPauseFR,
                                onFocused = {
                                    focusedButton = VideoControlButtonId.PLAY_PAUSE
                                    focusedIndex = 2
                                },
                                onClick = {
                                    handleTouchInteraction()
                                    togglePlayPause()
                                }
                            )

                            VideoGlassButton(
                                icon = Icons.Rounded.FastForward,
                                selected = !touchMode && focusedButton == VideoControlButtonId.NEXT_VIDEO,
                                focusEnabled = !touchMode,
                                focusRequester = nextFR,
                                onFocused = {
                                    focusedButton = VideoControlButtonId.NEXT_VIDEO
                                    focusedIndex = 3
                                },
                                onClick = {
                                    handleTouchInteraction()
                                    hClick()
                                    onNextVideo?.invoke()
                                    pingControls()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoGlassButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    focusEnabled: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .size(56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (focusEnabled && it.isFocused) onFocused()
            }
            .focusable(enabled = focusEnabled)
            .background(
                color = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                shape = shape
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.16f),
                shape = shape
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White
        )
    }
}