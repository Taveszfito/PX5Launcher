package com.dueboysenberry1226.px5launcher.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueItemUi(
    val queueId: Long,
    val title: String,
    val durationMs: Long? = null
)

data class MediaUiState(
    val hasSession: Boolean = false,
    val title: String = "Nincs lejátszás",
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSeek: Boolean = false,
    val queue: List<QueueItemUi> = emptyList(),
    val canSkipToQueueItem: Boolean = false
)

object MediaControllerRepo {

    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var nlsComponent: ComponentName? = null

    private var mediaSessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private var audioManager: AudioManager? = null

    fun init(context: Context) {
        // hívhatod egyszer app induláskor
        appContext = context.applicationContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    fun onListenerConnected(service: Context) {
        val ctx = appContext ?: service.applicationContext
        if (mediaSessionManager == null) init(ctx)

        nlsComponent = ComponentName(ctx, PX5NotificationListener::class.java)

        try {
            val sessions = mediaSessionManager?.getActiveSessions(nlsComponent)
            pickBestSession(sessions.orEmpty())
        } catch (_: SecurityException) {
            // nincs meg az értesítés-hozzáférés
            _state.value = MediaUiState(
                hasSession = false,
                title = "Adj értesítés-hozzáférést"
            )
        }

        mediaSessionManager?.addOnActiveSessionsChangedListener(
            sessionsChangedListener,
            nlsComponent
        )
    }

    fun onListenerDisconnected() {
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        detachController()
        _state.value = MediaUiState()
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            pickBestSession(controllers.orEmpty())
        }

    private fun pickBestSession(list: List<MediaController>) {
        // “legjobb” = ami játszik, különben első
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()

        if (best?.sessionToken == controller?.sessionToken) {
            refreshFromController(best)
            return
        }

        detachController()
        controller = best
        attachController(best)
        refreshFromController(best)
    }

    private fun attachController(c: MediaController?) {
        c ?: return
        c.registerCallback(controllerCallback)
    }

    private fun detachController() {
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            refreshFromController(controller)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            refreshFromController(controller)
        }

        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            refreshFromController(controller)
        }
    }

    private fun refreshFromController(c: MediaController?) {
        if (c == null) {
            _state.value = MediaUiState()
            return
        }

        val md = c.metadata
        val ps = c.playbackState

        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: "Nincs lejátszás"
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val isPlaying = ps?.state == PlaybackState.STATE_PLAYING
        val canSeek = ps != null && (ps.actions and PlaybackState.ACTION_SEEK_TO) != 0L

        val canSkipToQueueItem = ps != null &&
                (ps.actions and PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM) != 0L

        val queueUi: List<QueueItemUi> = c.queue
            ?.mapNotNull { qi ->
                val t = qi.description?.title?.toString()?.trim().orEmpty()
                if (t.isBlank()) null else QueueItemUi(queueId = qi.queueId, title = t)
            }
            .orEmpty()

        // position becslés play közben
        val position = estimatePosition(ps, isPlaying)

        _state.value = MediaUiState(
            hasSession = true,
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            positionMs = position.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
            canSeek = canSeek,
            queue = queueUi,
            canSkipToQueueItem = canSkipToQueueItem
        )
    }

    private fun estimatePosition(ps: PlaybackState?, isPlaying: Boolean): Long {
        if (ps == null) return 0L
        val base = ps.position
        if (!isPlaying) return base

        val timeDelta = SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime
        val speed = ps.playbackSpeed.takeIf { it > 0f } ?: 1f
        return base + (timeDelta * speed).toLong()
    }

    // ---- Controls ----

    fun togglePlayPause() {
        val c = controller ?: return
        val ps = c.playbackState
        val playing = ps?.state == PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }

    fun seekTo(ms: Long) {
        val c = controller ?: return
        val st = _state.value
        if (!st.canSeek) return
        c.transportControls.seekTo(ms.coerceIn(0L, st.durationMs.coerceAtLeast(0L)))
    }

    fun skipToQueueItem(queueId: Long) {
        val c = controller ?: return
        val ps = c.playbackState ?: return
        val ok = (ps.actions and PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM) != 0L
        if (!ok) return
        c.transportControls.skipToQueueItem(queueId)
    }

    fun volumeUp() {
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
    }

    fun volumeDown() {
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
    }
}