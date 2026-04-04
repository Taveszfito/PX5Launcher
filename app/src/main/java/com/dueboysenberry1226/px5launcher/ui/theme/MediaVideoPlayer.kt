package com.dueboysenberry1226.px5launcher.ui.theme

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@kotlin.OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun MediaVideoPlayer(
    videoUri: Uri,
    videoName: String?,
    modifier: Modifier = Modifier,
    startPlaying: Boolean = true,
    vibrationEnabled: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun getVolumePercent(): Int {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((currentVolume.toFloat() / maxVolume.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }

    var controlsVisible by rememberSaveable(videoUri.toString()) { mutableStateOf(true) }
    var isScrubbing by rememberSaveable(videoUri.toString()) { mutableStateOf(false) }
    var scrubPositionMs by rememberSaveable(videoUri.toString()) { mutableLongStateOf(0L) }
    var displayedPositionMs by rememberSaveable(videoUri.toString()) { mutableLongStateOf(0L) }
    var displayedDurationMs by rememberSaveable(videoUri.toString()) { mutableLongStateOf(0L) }
    var isPlaying by rememberSaveable(videoUri.toString()) { mutableStateOf(startPlaying) }
    var volumePercent by rememberSaveable(videoUri.toString()) { mutableStateOf(getVolumePercent()) }
    var resolvedTitle by rememberSaveable(videoUri.toString()) { mutableStateOf(videoName ?: "Videó") }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = startPlaying
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration
                displayedDurationMs = if (dur > 0L) dur else 0L
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                val metaTitle =
                    mediaMetadata.displayTitle?.toString()
                        ?: mediaMetadata.title?.toString()

                resolvedTitle = when {
                    !metaTitle.isNullOrBlank() -> metaTitle
                    !videoName.isNullOrBlank() -> videoName
                    else -> "Videó"
                }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (!isScrubbing) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
                displayedPositionMs = pos
                displayedDurationMs = dur
            }
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isScrubbing, videoUri) {
        if (controlsVisible && isPlaying && !isScrubbing) {
            delay(5000)
            controlsVisible = false
        }
    }

    fun showControls() {
        controlsVisible = true
    }

    fun togglePlayPause() {
        hClick()
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        showControls()
    }

    fun seekBy(deltaMs: Long) {
        hClick()
        val dur = exoPlayer.duration.takeIf { it > 0L } ?: 0L
        val newPos = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, dur)
        exoPlayer.seekTo(newPos)
        displayedPositionMs = newPos
        showControls()
    }

    fun adjustVolume(direction: Int) {
        hClick()
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            0
        )
        volumePercent = getVolumePercent()
        showControls()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        controlsVisible = !controlsVisible
                    },
                    onLongClick = {
                        onBack?.invoke()
                    }
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        controllerAutoShow = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (controlsVisible) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xCC202020)
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                        .fillMaxWidth(0.34f)
                        .widthIn(min = 280.dp, max = 420.dp)
                        .heightIn(min = 220.dp)
                        .offset(y = 18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = resolvedTitle.ifBlank { "Videó" },
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Slider(
                                value = if (displayedDurationMs > 0L) {
                                    val base = if (isScrubbing) scrubPositionMs else displayedPositionMs
                                    (base.toFloat() / displayedDurationMs.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                },
                                onValueChange = { fraction ->
                                    isScrubbing = true
                                    scrubPositionMs =
                                        (displayedDurationMs.toFloat() * fraction).roundToLongSafe()
                                },
                                onValueChangeFinished = {
                                    val target = scrubPositionMs.coerceIn(0L, displayedDurationMs)
                                    exoPlayer.seekTo(target)
                                    displayedPositionMs = target
                                    isScrubbing = false
                                    showControls()
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatVideoTime(if (isScrubbing) scrubPositionMs else displayedPositionMs),
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp
                                )

                                Text(
                                    text = formatVideoTime(displayedDurationMs),
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VideoControlButton(
                                icon = Icons.Rounded.FastRewind,
                                onClick = { seekBy(-10_000L) },
                                modifier = Modifier.weight(1f)
                            )

                            VideoControlButton(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                onClick = { togglePlayPause() },
                                modifier = Modifier.weight(1f)
                            )

                            VideoControlButton(
                                icon = Icons.Rounded.FastForward,
                                onClick = { seekBy(10_000L) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoControlButton(
                                icon = Icons.Rounded.VolumeDown,
                                onClick = { adjustVolume(AudioManager.ADJUST_LOWER) },
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "$volumePercent%",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.alpha(0.88f)
                            )

                            VideoControlButton(
                                icon = Icons.Rounded.VolumeUp,
                                onClick = { adjustVolume(AudioManager.ADJUST_RAISE) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (onBack != null) {
                            Text(
                                text = "Hosszan nyomva: vissza",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun formatVideoTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun Float.roundToLongSafe(): Long {
    return if (this.isFinite()) {
        roundToInt().toLong()
    } else {
        0L
    }
}