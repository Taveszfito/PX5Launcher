@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.theme

import android.app.Dialog
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.PaddingValues
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.util.LruCache
import android.util.Size
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.media.MediaAlbum
import com.dueboysenberry1226.px5launcher.media.MediaEntry
import com.dueboysenberry1226.px5launcher.media.MediaKind
import com.dueboysenberry1226.px5launcher.media.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private object MediaBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L)
            .coerceAtMost(48L * 1024L * 1024L)
            .toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (get(key) == null) cache.put(key, bitmap)
    }
}

private val ViewerDecodeDispatcher = Dispatchers.IO.limitedParallelism(1)
private val ThumbDecodeDispatcher = Dispatchers.IO.limitedParallelism(2)

private fun cacheKey(uri: Uri, reqWidth: Int, reqHeight: Int): String =
    "${uri}|${reqWidth}x${reqHeight}"

private fun calculateInSampleSize(
    srcWidth: Int,
    srcHeight: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    var inSampleSize = 1

    if (srcHeight > reqHeight || srcWidth > reqWidth) {
        val halfHeight = srcHeight / 2
        val halfWidth = srcWidth / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize.coerceAtLeast(1)
}

private fun loadSampledBitmap(
    context: Context,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    val safeReqWidth = reqWidth.coerceAtLeast(1)
    val safeReqHeight = reqHeight.coerceAtLeast(1)
    val key = cacheKey(uri, safeReqWidth, safeReqHeight)

    MediaBitmapCache.get(key)?.let { return it }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            srcWidth = bounds.outWidth,
            srcHeight = bounds.outHeight,
            reqWidth = safeReqWidth,
            reqHeight = safeReqHeight
        )
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    } ?: return null

    MediaBitmapCache.put(key, bitmap)
    return bitmap
}

private fun loadThumbBitmap(
    context: Context,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int,
    kind: MediaKind
): Bitmap? {
    val safeReqWidth = reqWidth.coerceAtLeast(1)
    val safeReqHeight = reqHeight.coerceAtLeast(1)
    val key = cacheKey(uri, safeReqWidth, safeReqHeight)

    MediaBitmapCache.get(key)?.let { return it }

    val bitmap = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(safeReqWidth, safeReqHeight), null)
        } else {
            if (kind == MediaKind.IMAGE) {
                loadSampledBitmap(context, uri, safeReqWidth, safeReqHeight)
            } else {
                null
            }
        }
    }.getOrNull() ?: if (kind == MediaKind.VIDEO) {
        loadVideoFrameBitmap(
            context = context,
            uri = uri,
            reqWidth = safeReqWidth,
            reqHeight = safeReqHeight
        )
    } else {
        null
    } ?: return null

    MediaBitmapCache.put(key, bitmap)
    return bitmap
}

private fun loadVideoFrameBitmap(
    context: Context,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int
): Bitmap? {
    val retriever = MediaMetadataRetriever()

    return try {
        retriever.setDataSource(context, uri)

        val rawFrame =
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null

        val scaled = Bitmap.createScaledBitmap(
            rawFrame,
            reqWidth.coerceAtLeast(1),
            reqHeight.coerceAtLeast(1),
            true
        )

        if (scaled != rawFrame) {
            rawFrame.recycle()
        }

        scaled
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private enum class MediaScreen {
    HUB,
    IMAGES,
    VIDEOS,
    ALBUMS,
    ALBUM_CONTENT,
    VIEWER,
    VIDEO_PLAYER
}

private sealed class MediaGridItem {
    data object Back : MediaGridItem()
    data class Entry(val e: MediaEntry) : MediaGridItem()
    data class Album(val a: MediaAlbum) : MediaGridItem()
}

private enum class ViewerFocus {
    NONE,
    BACK,
    ROTATE,
    IMAGE,
    ZOOM_IN,
    ZOOM_OUT,
    NEXT,
    PREV,
    SHARE,
    DELETE,
    TOGGLE
}

private enum class ViewerCommandType {
    PAN_LEFT,
    PAN_RIGHT,
    PAN_UP,
    PAN_DOWN,
    ZOOM_IN,
    ZOOM_OUT,
    ROTATE_CLOCKWISE
}

private data class ViewerCommand(
    val type: ViewerCommandType,
    val nonce: Long = System.nanoTime()
)

@OptIn(UnstableApi::class)
@Composable
fun MediaRoute(
    onRequestBackToGames: () -> Unit,
    hubSelectionEnabled: Boolean,
    registerKeyHandler: (((KeyEvent) -> Boolean)) -> Unit,
    vibrationEnabled: Boolean
) {
    val context = LocalContext.current
    val repo = remember { MediaStoreRepository(context) }

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    var hasPerm by remember { mutableStateOf(hasAllPermissions(context)) }
    var permissionButtonIndex by rememberSaveable { mutableIntStateOf(0) }
    var permissionButtonsFocused by rememberSaveable { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPerm = result.values.all { it }
    }

    var screen by rememberSaveable { mutableStateOf(MediaScreen.HUB) }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastContentSelectedIndex by rememberSaveable { mutableIntStateOf(0) }

    var currentAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentAlbumName by rememberSaveable { mutableStateOf<String?>(null) }

    var refreshTick by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(hasPerm) {
        if (hasPerm) refreshTick++
    }

    val images by produceState(initialValue = emptyList(), refreshTick) {
        value = if (!hasPerm) emptyList()
        else withContext(Dispatchers.Default) { repo.loadImages() }
    }

    val videos by produceState(initialValue = emptyList(), refreshTick) {
        value = if (!hasPerm) emptyList()
        else withContext(Dispatchers.Default) { repo.loadVideos() }
    }

    val albums by produceState(initialValue = emptyList(), refreshTick) {
        value = if (!hasPerm) emptyList()
        else withContext(Dispatchers.Default) { repo.loadAlbums() }
    }

    val albumContent by produceState(initialValue = emptyList(), refreshTick, currentAlbumId) {
        val bid = currentAlbumId
        value =
            if (!hasPerm || bid.isNullOrBlank()) emptyList()
            else withContext(Dispatchers.Default) { repo.loadAlbumContent(bid) }
    }

    val columns = 6

    var viewerEntries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(0) }
    var viewerFocus by rememberSaveable { mutableStateOf(ViewerFocus.ZOOM_IN) }
    var viewerLastSideFocus by rememberSaveable { mutableStateOf(ViewerFocus.ZOOM_IN) }
    var viewerPanMode by rememberSaveable { mutableStateOf(false) }
    var viewerCommand by remember { mutableStateOf<ViewerCommand?>(null) }
    var viewerPanelCollapsed by rememberSaveable { mutableStateOf(false) }

    var currentVideoEntry by remember { mutableStateOf<MediaEntry?>(null) }

    val viewerEntry = viewerEntries.getOrNull(viewerIndex)

    val backStack = remember { mutableStateListOf<MediaScreen>() }

    fun navigateTo(next: MediaScreen) {
        if (screen != next) backStack.add(screen)
        screen = next
        selectedIndex = 0
    }

    fun goBackOneLevel() {
        if (screen == MediaScreen.VIEWER) {
            viewerEntries = emptyList()
            viewerIndex = 0
            viewerPanMode = false
            viewerCommand = null
            viewerPanelCollapsed = false
        }

        if (screen == MediaScreen.VIDEO_PLAYER) {
            currentVideoEntry = null
        }

        val prev = backStack.lastOrNull()
        if (prev != null) {
            backStack.removeAt(backStack.lastIndex)
            screen = prev
        } else {
            screen = MediaScreen.HUB
        }
        selectedIndex = 0

        if (screen != MediaScreen.ALBUM_CONTENT) {
            currentAlbumId = null
            currentAlbumName = null
        }
    }

    fun openViewer(e: MediaEntry) {
        val source = when (screen) {
            MediaScreen.IMAGES -> images.filter { it.kind == MediaKind.IMAGE }
            MediaScreen.ALBUM_CONTENT -> albumContent.filter { it.kind == MediaKind.IMAGE }
            else -> images.filter { it.kind == MediaKind.IMAGE }
        }

        viewerEntries = source
        viewerIndex = source.indexOfFirst { it.id == e.id }.takeIf { it >= 0 } ?: 0
        viewerFocus = ViewerFocus.ZOOM_IN
        viewerLastSideFocus = ViewerFocus.ZOOM_IN
        viewerPanMode = false
        viewerCommand = null
        navigateTo(MediaScreen.VIEWER)
    }

    fun openVideoPlayer(entry: MediaEntry) {
        currentVideoEntry = entry
        navigateTo(MediaScreen.VIDEO_PLAYER)
    }

    fun currentVideoSource(): List<MediaEntry> = when (screen) {
        MediaScreen.VIDEOS -> videos.filter { it.kind == MediaKind.VIDEO }
        MediaScreen.ALBUM_CONTENT -> albumContent.filter { it.kind == MediaKind.VIDEO }
        else -> videos.filter { it.kind == MediaKind.VIDEO }
    }

    fun stepVideo(direction: Int) {
        val current = currentVideoEntry ?: return
        val source = currentVideoSource()
        if (source.isEmpty()) return

        val currentIndex = source.indexOfFirst { it.id == current.id }
        if (currentIndex < 0) return

        val nextIndex = (currentIndex + direction).coerceIn(0, source.lastIndex)
        if (nextIndex != currentIndex) {
            currentVideoEntry = source[nextIndex]
        }
    }

    fun shareViewerEntry() {
        val entry = viewerEntry ?: return
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, entry.uri)
            clipData = ClipData.newUri(
                context.contentResolver,
                entry.displayName ?: "image",
                entry.uri
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, entry.displayName ?: "Share image"))
    }

    fun deleteViewerEntry() {
        val entry = viewerEntry ?: return
        val deleted = runCatching {
            context.contentResolver.delete(entry.uri, null, null) > 0
        }.getOrDefault(false)

        if (!deleted) return

        val newList = viewerEntries.filterNot { it.id == entry.id }
        viewerEntries = newList
        refreshTick++

        if (newList.isEmpty()) {
            goBackOneLevel()
        } else {
            viewerIndex = viewerIndex.coerceIn(0, newList.lastIndex)
            viewerFocus = ViewerFocus.ZOOM_IN
            viewerLastSideFocus = ViewerFocus.ZOOM_IN
            viewerPanMode = false
            viewerCommand = null
        }
    }

    val gridItems: List<MediaGridItem> = remember(screen, images, videos, albums, albumContent) {
        when (screen) {
            MediaScreen.IMAGES -> buildList {
                add(MediaGridItem.Back)
                addAll(images.map { MediaGridItem.Entry(it) })
            }

            MediaScreen.VIDEOS -> buildList {
                add(MediaGridItem.Back)
                addAll(videos.map { MediaGridItem.Entry(it) })
            }

            MediaScreen.ALBUMS -> buildList {
                add(MediaGridItem.Back)
                addAll(albums.map { MediaGridItem.Album(it) })
            }

            MediaScreen.ALBUM_CONTENT -> buildList {
                add(MediaGridItem.Back)
                addAll(albumContent.map { MediaGridItem.Entry(it) })
            }

            else -> emptyList()
        }
    }

    fun isContentGrid(s: MediaScreen): Boolean =
        s == MediaScreen.IMAGES ||
                s == MediaScreen.VIDEOS ||
                s == MediaScreen.ALBUMS ||
                s == MediaScreen.ALBUM_CONTENT

    LaunchedEffect(hubSelectionEnabled, screen, gridItems.size) {
        if (!isContentGrid(screen)) return@LaunchedEffect

        if (!hubSelectionEnabled) {
            if (selectedIndex >= 0) lastContentSelectedIndex = selectedIndex
            selectedIndex = -1
        } else {
            if (selectedIndex == -1) {
                val last = gridItems.lastIndex
                selectedIndex =
                    if (last >= 0) lastContentSelectedIndex.coerceIn(0, last) else 0
            }
        }
    }

    LaunchedEffect(screen, gridItems.size, hubSelectionEnabled) {
        if (screen == MediaScreen.HUB) {
            selectedIndex = selectedIndex.coerceIn(0, 2)
        } else if (isContentGrid(screen)) {
            val last = gridItems.lastIndex
            selectedIndex = when {
                selectedIndex == -1 && !hubSelectionEnabled -> -1
                last >= 0 -> selectedIndex.coerceIn(0, last)
                else -> 0
            }
        } else {
            selectedIndex = 0
        }
    }

    val internalKeyHandler: (KeyEvent) -> Boolean = internalKeyHandler@{ e ->
        val nk = e.nativeKeyEvent
        val code = nk.keyCode
        val action = nk.action

        val okCodes = setOf(
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            AndroidKeyEvent.KEYCODE_BUTTON_A
        )
        val backCodes = setOf(
            AndroidKeyEvent.KEYCODE_BACK,
            AndroidKeyEvent.KEYCODE_BUTTON_B
        )

        val isOk = code in okCodes
        val isBack = code in backCodes

        if (isOk || isBack) {
            when (action) {
                AndroidKeyEvent.ACTION_DOWN -> return@internalKeyHandler true
                AndroidKeyEvent.ACTION_UP -> Unit
                else -> return@internalKeyHandler true
            }
        } else {
            if (action != AndroidKeyEvent.ACTION_DOWN) return@internalKeyHandler false
        }

        if (isOk || isBack) hClick()

        if (!hubSelectionEnabled && screen == MediaScreen.HUB && code == AndroidKeyEvent.KEYCODE_DPAD_UP) {
            return@internalKeyHandler false
        }

        if (!hasPerm) {
            return@internalKeyHandler when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    permissionButtonsFocused = true
                    if (permissionButtonIndex > 0) permissionButtonIndex--
                    true
                }

                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    permissionButtonsFocused = true
                    if (permissionButtonIndex < 1) permissionButtonIndex++
                    true
                }

                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    permissionButtonsFocused = true
                    true
                }

                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    permissionButtonsFocused = false
                    false
                }

                in backCodes -> {
                    permissionButtonsFocused = true
                    onRequestBackToGames()
                    true
                }

                in okCodes -> {
                    permissionButtonsFocused = true
                    if (permissionButtonIndex == 0) {
                        permLauncher.launch(MediaStoreRepository.requiredPermissions())
                    } else {
                        onRequestBackToGames()
                    }
                    true
                }

                else -> false
            }
        }

        fun ensureSelectionForGrid() {
            if (selectedIndex == -1) {
                val last = gridItems.lastIndex
                selectedIndex =
                    if (last >= 0) lastContentSelectedIndex.coerceIn(0, last) else 0
            }
        }

        when (screen) {
            MediaScreen.HUB -> {
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (hubSelectionEnabled && selectedIndex > 0) selectedIndex--
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (hubSelectionEnabled && selectedIndex < 2) selectedIndex++
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_UP -> false
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true

                    in okCodes -> {
                        if (!hubSelectionEnabled) return@internalKeyHandler false
                        when (selectedIndex) {
                            0 -> navigateTo(MediaScreen.IMAGES)
                            1 -> navigateTo(MediaScreen.VIDEOS)
                            else -> navigateTo(MediaScreen.ALBUMS)
                        }
                        true
                    }

                    in backCodes -> {
                        onRequestBackToGames()
                        true
                    }

                    else -> false
                }
            }

            MediaScreen.IMAGES,
            MediaScreen.VIDEOS,
            MediaScreen.ALBUMS,
            MediaScreen.ALBUM_CONTENT -> {
                val lastIndex = gridItems.lastIndex

                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        ensureSelectionForGrid()
                        if (selectedIndex > 0) selectedIndex--
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        ensureSelectionForGrid()
                        if (selectedIndex < lastIndex) selectedIndex++
                        true
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        ensureSelectionForGrid()
                        val next = selectedIndex - columns
                        if (next >= 0) {
                            selectedIndex = next
                            true
                        } else {
                            false
                        }
                    }

                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        ensureSelectionForGrid()
                        val next = selectedIndex + columns
                        if (next <= lastIndex) selectedIndex = next
                        true
                    }

                    in okCodes -> {
                        ensureSelectionForGrid()
                        when (val it = gridItems.getOrNull(selectedIndex)) {
                            is MediaGridItem.Back -> goBackOneLevel()

                            is MediaGridItem.Entry -> {
                                if (it.e.kind == MediaKind.VIDEO) {
                                    openVideoPlayer(it.e)
                                } else {
                                    openViewer(it.e)
                                }
                            }

                            is MediaGridItem.Album -> {
                                currentAlbumId = it.a.bucketId
                                currentAlbumName = it.a.bucketName
                                navigateTo(MediaScreen.ALBUM_CONTENT)
                            }

                            null -> Unit
                        }
                        true
                    }

                    in backCodes -> {
                        goBackOneLevel()
                        true
                    }

                    else -> false
                }
            }

            MediaScreen.VIEWER -> {
                fun moveViewerFocus(code: Int) {
                    viewerFocus = when (viewerFocus) {
                        ViewerFocus.NONE -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                            AndroidKeyEvent.KEYCODE_DPAD_UP,
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> ViewerFocus.ZOOM_IN
                            else -> ViewerFocus.NONE
                        }

                        ViewerFocus.BACK -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> ViewerFocus.IMAGE
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> ViewerFocus.ROTATE
                            else -> ViewerFocus.BACK
                        }

                        ViewerFocus.ROTATE -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> ViewerFocus.BACK
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> ViewerFocus.IMAGE
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> ViewerFocus.IMAGE
                            else -> ViewerFocus.ROTATE
                        }

                        ViewerFocus.IMAGE -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> ViewerFocus.ROTATE
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> ViewerFocus.TOGGLE
                            else -> ViewerFocus.IMAGE
                        }

                        ViewerFocus.NEXT -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> ViewerFocus.PREV
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> ViewerFocus.TOGGLE
                            else -> ViewerFocus.NEXT
                        }

                        ViewerFocus.PREV -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> ViewerFocus.ZOOM_IN
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> ViewerFocus.NEXT
                            else -> ViewerFocus.PREV
                        }

                        ViewerFocus.ZOOM_IN -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> ViewerFocus.ZOOM_OUT
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> ViewerFocus.PREV
                            else -> ViewerFocus.ZOOM_IN
                        }

                        ViewerFocus.ZOOM_OUT -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> ViewerFocus.IMAGE
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> ViewerFocus.ZOOM_IN
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> ViewerFocus.SHARE
                            else -> ViewerFocus.ZOOM_OUT
                        }

                        ViewerFocus.SHARE -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> ViewerFocus.IMAGE
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> ViewerFocus.ZOOM_OUT
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> ViewerFocus.DELETE
                            else -> ViewerFocus.SHARE
                        }

                        ViewerFocus.DELETE -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> ViewerFocus.IMAGE
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> ViewerFocus.SHARE
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> ViewerFocus.TOGGLE
                            else -> ViewerFocus.DELETE
                        }

                        ViewerFocus.TOGGLE -> when (code) {
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT ->
                                if (viewerPanelCollapsed) ViewerFocus.IMAGE else ViewerFocus.DELETE

                            AndroidKeyEvent.KEYCODE_DPAD_UP ->
                                if (viewerPanelCollapsed) ViewerFocus.IMAGE else ViewerFocus.NEXT

                            else -> ViewerFocus.TOGGLE
                        }
                    }

                    if (viewerFocus != ViewerFocus.IMAGE && viewerFocus != ViewerFocus.NONE) {
                        viewerLastSideFocus = viewerFocus
                    }
                }

                if (viewerPanMode) {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            viewerPanelCollapsed = true
                            viewerCommand = ViewerCommand(ViewerCommandType.PAN_LEFT)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            viewerPanelCollapsed = true
                            viewerCommand = ViewerCommand(ViewerCommandType.PAN_RIGHT)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            viewerPanelCollapsed = true
                            viewerCommand = ViewerCommand(ViewerCommandType.PAN_UP)
                            true
                        }

                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            viewerPanelCollapsed = true
                            viewerCommand = ViewerCommand(ViewerCommandType.PAN_DOWN)
                            true
                        }

                        in okCodes, in backCodes -> {
                            viewerPanMode = false
                            viewerFocus = ViewerFocus.IMAGE
                            true
                        }

                        else -> true
                    }
                } else {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            moveViewerFocus(code)
                            true
                        }

                        in okCodes -> {
                            when (viewerFocus) {
                                ViewerFocus.NONE -> Unit

                                ViewerFocus.BACK -> {
                                    goBackOneLevel()
                                }

                                ViewerFocus.ROTATE -> {
                                    viewerCommand = ViewerCommand(ViewerCommandType.ROTATE_CLOCKWISE)
                                }

                                ViewerFocus.IMAGE -> {
                                    viewerPanelCollapsed = true
                                    viewerPanMode = true
                                }

                                ViewerFocus.ZOOM_IN -> {
                                    viewerCommand = ViewerCommand(ViewerCommandType.ZOOM_IN)
                                }

                                ViewerFocus.ZOOM_OUT -> {
                                    viewerCommand = ViewerCommand(ViewerCommandType.ZOOM_OUT)
                                }

                                ViewerFocus.NEXT -> {
                                    if (viewerIndex < viewerEntries.lastIndex) {
                                        viewerIndex++
                                    }
                                }

                                ViewerFocus.PREV -> {
                                    if (viewerIndex > 0) {
                                        viewerIndex--
                                    }
                                }

                                ViewerFocus.SHARE -> {
                                    shareViewerEntry()
                                }

                                ViewerFocus.DELETE -> {
                                    deleteViewerEntry()
                                }

                                ViewerFocus.TOGGLE -> {
                                    viewerPanelCollapsed = !viewerPanelCollapsed
                                }
                            }
                            true
                        }

                        in backCodes -> {
                            goBackOneLevel()
                            true
                        }

                        else -> true
                    }
                }
            }

            MediaScreen.VIDEO_PLAYER -> {
                when (code) {
                    AndroidKeyEvent.KEYCODE_BACK,
                    AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                        goBackOneLevel()
                        true
                    }

                    else -> false
                }
            }
        }
    }

    SideEffect {
        registerKeyHandler(internalKeyHandler)
    }

    Box(
        Modifier.fillMaxSize()
    ) {
        if (!hasPerm) {
            MediaPermissionCard(
                selectedIndex = permissionButtonIndex,
                buttonsFocused = permissionButtonsFocused,
                onSelect = {
                    permissionButtonsFocused = true
                    permissionButtonIndex = it
                },
                onRequest = {
                    hClick()
                    permLauncher.launch(MediaStoreRepository.requiredPermissions())
                },
                onBack = {
                    hClick()
                    onRequestBackToGames()
                }
            )
            return@Box
        }

        when (screen) {
            MediaScreen.HUB -> {
                MediaHub(
                    selectedIndex = selectedIndex,
                    hubSelectionEnabled = hubSelectionEnabled,
                    onSelect = { selectedIndex = it },
                    onOpenImages = { navigateTo(MediaScreen.IMAGES) },
                    onOpenVideos = { navigateTo(MediaScreen.VIDEOS) },
                    onOpenAlbums = { navigateTo(MediaScreen.ALBUMS) },
                    vibrationEnabled = vibrationEnabled
                )
            }

            MediaScreen.IMAGES -> {
                MediaGrid(
                    title = stringResource(R.string.media_title_images),
                    items = gridItems,
                    selectedIndex = selectedIndex,
                    columns = columns,
                    onSelectChange = { selectedIndex = it },
                    onBack = { goBackOneLevel() },
                    onOpenEntry = { e -> openViewer(e) },
                    onOpenAlbum = null,
                    vibrationEnabled = vibrationEnabled
                )
            }

            MediaScreen.VIDEOS -> {
                MediaGrid(
                    title = stringResource(R.string.media_title_videos),
                    items = gridItems,
                    selectedIndex = selectedIndex,
                    columns = columns,
                    onSelectChange = { selectedIndex = it },
                    onBack = { goBackOneLevel() },
                    onOpenEntry = { e -> openVideoPlayer(e) },
                    onOpenAlbum = null,
                    vibrationEnabled = vibrationEnabled
                )
            }

            MediaScreen.ALBUMS -> {
                MediaGrid(
                    title = stringResource(R.string.media_title_albums),
                    items = gridItems,
                    selectedIndex = selectedIndex,
                    columns = columns,
                    onSelectChange = { selectedIndex = it },
                    onBack = { goBackOneLevel() },
                    onOpenEntry = null,
                    onOpenAlbum = { a ->
                        currentAlbumId = a.bucketId
                        currentAlbumName = a.bucketName
                        navigateTo(MediaScreen.ALBUM_CONTENT)
                    },
                    vibrationEnabled = vibrationEnabled
                )
            }

            MediaScreen.ALBUM_CONTENT -> {
                MediaGrid(
                    title = currentAlbumName ?: stringResource(R.string.media_title_album_fallback),
                    items = gridItems,
                    selectedIndex = selectedIndex,
                    columns = columns,
                    onSelectChange = { selectedIndex = it },
                    onBack = { goBackOneLevel() },
                    onOpenEntry = { e ->
                        if (e.kind == MediaKind.VIDEO) openVideoPlayer(e) else openViewer(e)
                    },
                    onOpenAlbum = null,
                    vibrationEnabled = vibrationEnabled
                )
            }

            MediaScreen.VIEWER -> {
                MediaImageViewer(
                    entry = viewerEntry,
                    previousEntry = viewerEntries.getOrNull(viewerIndex - 1),
                    nextEntry = viewerEntries.getOrNull(viewerIndex + 1),
                    currentIndex = viewerIndex,
                    totalCount = viewerEntries.size,
                    focus = viewerFocus,
                    panMode = viewerPanMode,
                    panelCollapsed = viewerPanelCollapsed,
                    command = viewerCommand,
                    onCommandConsumed = { viewerCommand = null },
                    onFocusChange = {
                        viewerFocus = it
                        if (it != ViewerFocus.IMAGE && it != ViewerFocus.NONE) {
                            viewerLastSideFocus = it
                        }
                    },
                    onPanModeChange = {
                        viewerPanMode = it
                        if (it) viewerPanelCollapsed = true
                    },
                    onPanelCollapsedChange = { viewerPanelCollapsed = it },
                    onNext = {
                        if (viewerIndex < viewerEntries.lastIndex) viewerIndex++
                    },
                    onPrevious = {
                        if (viewerIndex > 0) viewerIndex--
                    },
                    onShare = { shareViewerEntry() },
                    onDelete = { deleteViewerEntry() },
                    onBack = { goBackOneLevel() },
                    vibrationEnabled = vibrationEnabled
                )
            }

            MediaScreen.VIDEO_PLAYER -> {
                val videoEntry = currentVideoEntry
                if (videoEntry != null) {
                    Dialog(
                        onDismissRequest = { goBackOneLevel() },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = false
                        )
                    ) {
                        VideoPlayerDialogImmersiveEffect()

                        MediaVideoPlayer(
                            videoUri = videoEntry.uri,
                            videoName = videoEntry.displayName,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            vibrationEnabled = vibrationEnabled,
                            onBack = { goBackOneLevel() },
                            onPreviousVideo = { stepVideo(-1) },
                            onNextVideo = { stepVideo(+1) }
                        )
                    }
                } else {
                    goBackOneLevel()
                }
            }
        }
    }
}

private fun hasAllPermissions(ctx: Context): Boolean {
    val perms = MediaStoreRepository.requiredPermissions()
    return perms.all { p ->
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun MediaPermissionCard(
    selectedIndex: Int,
    buttonsFocused: Boolean,
    onSelect: (Int) -> Unit,
    onRequest: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.media_perm_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.media_perm_desc),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TouchTextButton(
                    text = stringResource(R.string.media_perm_request),
                    selected = buttonsFocused && selectedIndex == 0,
                    onClick = {
                        onSelect(0)
                        onRequest()
                    }
                )
                TouchTextButton(
                    text = stringResource(R.string.common_back),
                    selected = buttonsFocused && selectedIndex == 1,
                    onClick = {
                        onSelect(1)
                        onBack()
                    },
                    alpha = 0.85f
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TouchTextButton(
    text: String,
    onClick: () -> Unit,
    alpha: Float = 1f,
    selected: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
        )
    }
}

@Composable
private fun MediaHub(
    selectedIndex: Int,
    hubSelectionEnabled: Boolean,
    onSelect: (Int) -> Unit,
    onOpenImages: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenAlbums: () -> Unit,
    vibrationEnabled: Boolean
) {
    val context = LocalContext.current

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(70.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaBigButton(
                icon = "🖼️",
                text = stringResource(R.string.media_title_images),
                selected = hubSelectionEnabled && selectedIndex == 0,
                onClick = {
                    hClick()
                    onSelect(0)
                    onOpenImages()
                }
            )
            MediaBigButton(
                icon = "🎬",
                text = stringResource(R.string.media_title_videos),
                selected = hubSelectionEnabled && selectedIndex == 1,
                onClick = {
                    hClick()
                    onSelect(1)
                    onOpenVideos()
                }
            )
            MediaBigButton(
                icon = "📁",
                text = stringResource(R.string.media_title_albums),
                selected = hubSelectionEnabled && selectedIndex == 2,
                onClick = {
                    hClick()
                    onSelect(2)
                    onOpenAlbums()
                }
            )
        }
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaBigButton(
    icon: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(if (selected) 1.05f else 1.0f, label = "mediaBigScale")
    val bgAlpha = if (selected) 0.12f else 0.06f

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = bgAlpha)),
        modifier = Modifier
            .width(220.dp)
            .height(170.dp)
            .scale(scale)
            .then(
                if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                else Modifier
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 34.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MediaGrid(
    title: String,
    items: List<MediaGridItem>,
    selectedIndex: Int,
    columns: Int,
    onSelectChange: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenEntry: ((MediaEntry) -> Unit)?,
    onOpenAlbum: ((MediaAlbum) -> Unit)?,
    vibrationEnabled: Boolean
) {
    val context = LocalContext.current

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    val gridState = rememberLazyGridState()

    LaunchedEffect(selectedIndex, items.size) {
        if (items.isNotEmpty() && selectedIndex >= 0) {
            gridState.animateScrollToItem(selectedIndex.coerceIn(0, items.lastIndex))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 8.dp, bottom = 14.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp)
        ) {
            itemsIndexed(items) { index, item ->
                when (item) {
                    is MediaGridItem.Back -> {
                        MediaBigButton(
                            icon = "↩",
                            text = stringResource(R.string.common_back),
                            selected = selectedIndex == index,
                            onClick = {
                                hClick()
                                onSelectChange(index)
                                onBack()
                            }
                        )
                    }

                    is MediaGridItem.Entry -> {
                        MediaThumbTile(
                            entry = item.e,
                            selected = selectedIndex == index,
                            onClick = {
                                hClick()
                                onSelectChange(index)
                                onOpenEntry?.invoke(item.e)
                            }
                        )
                    }

                    is MediaGridItem.Album -> {
                        MediaBigButton(
                            icon = "📁",
                            text = item.a.bucketName.ifBlank {
                                stringResource(R.string.media_title_album_fallback)
                            },
                            selected = selectedIndex == index,
                            onClick = {
                                hClick()
                                onSelectChange(index)
                                onOpenAlbum?.invoke(item.a)
                            }
                        )
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumbTile(
    entry: MediaEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbSize by remember { mutableStateOf(IntSize.Zero) }

    val bmp: Bitmap? by produceState(
        initialValue = null,
        entry.uri,
        thumbSize
    ) {
        val w = thumbSize.width.coerceAtLeast(1)
        val h = thumbSize.height.coerceAtLeast(1)
        value = withContext(ThumbDecodeDispatcher) {
            runCatching {
                loadThumbBitmap(
                    context = context,
                    uri = entry.uri,
                    reqWidth = w,
                    reqHeight = h,
                    kind = entry.kind
                )
            }.getOrNull()
        }
    }

    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .height(170.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .then(
                if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                else Modifier
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onSizeChanged { thumbSize = it }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (entry.kind == MediaKind.VIDEO) "🎬" else "🖼️",
                    fontSize = 22.sp
                )
            }
        }



        if (entry.kind == MediaKind.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.46f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "▶",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun MediaImageViewer(
    entry: MediaEntry?,
    previousEntry: MediaEntry?,
    nextEntry: MediaEntry?,
    currentIndex: Int,
    totalCount: Int,
    focus: ViewerFocus,
    panMode: Boolean,
    panelCollapsed: Boolean,
    command: ViewerCommand?,
    onCommandConsumed: () -> Unit,
    onFocusChange: (ViewerFocus) -> Unit,
    onPanModeChange: (Boolean) -> Unit,
    onPanelCollapsedChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    vibrationEnabled: Boolean
) {
    val context = LocalContext.current

    fun hClick() {
        if (vibrationEnabled) Haptics.click(context)
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val fallbackDecodeWidth = remember(configuration.screenWidthDp, density) {
        with(density) { (configuration.screenWidthDp.dp * 0.72f).roundToPx().coerceAtLeast(1) }
    }
    val fallbackDecodeHeight = remember(configuration.screenHeightDp, density) {
        with(density) { (configuration.screenHeightDp.dp * 0.72f).roundToPx().coerceAtLeast(1) }
    }

    val targetDecodeWidth = remember(viewportSize, fallbackDecodeWidth) {
        viewportSize.width.takeIf { it > 0 }?.coerceAtMost(1600) ?: fallbackDecodeWidth.coerceAtMost(1600)
    }
    val targetDecodeHeight = remember(viewportSize, fallbackDecodeHeight) {
        viewportSize.height.takeIf { it > 0 }?.coerceAtMost(1600) ?: fallbackDecodeHeight.coerceAtMost(1600)
    }

    val previewDecodeWidth = remember(targetDecodeWidth) { max(1, targetDecodeWidth / 2) }
    val previewDecodeHeight = remember(targetDecodeHeight) { max(1, targetDecodeHeight / 2) }

    val bmp: Bitmap? by produceState(
        initialValue = entry?.uri?.let {
            MediaBitmapCache.get(cacheKey(it, targetDecodeWidth, targetDecodeHeight))
                ?: MediaBitmapCache.get(cacheKey(it, previewDecodeWidth, previewDecodeHeight))
        },
        entry?.uri,
        targetDecodeWidth,
        targetDecodeHeight,
        previewDecodeWidth,
        previewDecodeHeight
    ) {
        val u = entry?.uri ?: return@produceState

        MediaBitmapCache.get(cacheKey(u, targetDecodeWidth, targetDecodeHeight))?.let {
            value = it
            return@produceState
        }

        if (value == null) {
            value = withContext(ThumbDecodeDispatcher) {
                runCatching {
                    loadSampledBitmap(
                        context = context,
                        uri = u,
                        reqWidth = previewDecodeWidth,
                        reqHeight = previewDecodeHeight
                    )
                }.getOrNull()
            }
        }

        val fullBitmap = withContext(ViewerDecodeDispatcher) {
            runCatching {
                loadSampledBitmap(
                    context = context,
                    uri = u,
                    reqWidth = targetDecodeWidth,
                    reqHeight = targetDecodeHeight
                )
            }.getOrNull()
        }

        if (fullBitmap != null) value = fullBitmap
    }

    LaunchedEffect(previousEntry?.uri, nextEntry?.uri, previewDecodeWidth, previewDecodeHeight) {
        withContext(ThumbDecodeDispatcher) {
            previousEntry?.uri?.let { uri ->
                runCatching {
                    loadSampledBitmap(
                        context = context,
                        uri = uri,
                        reqWidth = previewDecodeWidth,
                        reqHeight = previewDecodeHeight
                    )
                }
            }
            nextEntry?.uri?.let { uri ->
                runCatching {
                    loadSampledBitmap(
                        context = context,
                        uri = uri,
                        reqWidth = previewDecodeWidth,
                        reqHeight = previewDecodeHeight
                    )
                }
            }
        }
    }

    val fileSizeText by produceState(initialValue = "—", entry?.uri) {
        val u = entry?.uri ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(u, "r")?.use { afd ->
                    val len = afd.length
                    if (len > 0) Formatter.formatShortFileSize(context, len) else "—"
                } ?: "—"
            }.getOrDefault("—")
        }
    }

    val dateText = remember(entry?.dateAddedSec) {
        entry?.dateAddedSec?.takeIf { it > 0L }?.let { sec ->
            java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(sec * 1000L))
        } ?: "—"
    }

    var scale by remember(entry?.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(entry?.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(entry?.id) { mutableFloatStateOf(0f) }
    var rotation by remember(entry?.id) { mutableFloatStateOf(0f) }

    fun clampOffset(
        targetScale: Float,
        targetOffsetX: Float,
        targetOffsetY: Float
    ): Pair<Float, Float> {
        val bmpW = bmp?.width?.toFloat() ?: 0f
        val bmpH = bmp?.height?.toFloat() ?: 0f
        val vpW = viewportSize.width.toFloat()
        val vpH = viewportSize.height.toFloat()

        if (bmpW <= 0f || bmpH <= 0f || vpW <= 0f || vpH <= 0f) {
            return 0f to 0f
        }

        val isQuarterTurn = (rotation % 180f) != 0f
        val effectiveBmpW = if (isQuarterTurn) bmpH else bmpW
        val effectiveBmpH = if (isQuarterTurn) bmpW else bmpH

        val fitScale = min(vpW / effectiveBmpW, vpH / effectiveBmpH)
        val displayedW = effectiveBmpW * fitScale * targetScale
        val displayedH = effectiveBmpH * fitScale * targetScale

        val maxX = max(0f, (displayedW - vpW) / 2f)
        val maxY = max(0f, (displayedH - vpH) / 2f)

        return targetOffsetX.coerceIn(-maxX, maxX) to targetOffsetY.coerceIn(-maxY, maxY)
    }

    fun applyZoom(multiplier: Float) {
        val newScale = (scale * multiplier).coerceIn(1f, 4f)
        val clamped = clampOffset(newScale, offsetX, offsetY)
        scale = newScale
        offsetX = clamped.first
        offsetY = clamped.second
    }

    fun nudge(dx: Float, dy: Float) {
        val clamped = clampOffset(scale, offsetX + dx, offsetY + dy)
        offsetX = clamped.first
        offsetY = clamped.second
    }

    fun rotateClockwise() {
        rotation = (rotation + 90f) % 360f
        val clamped = clampOffset(scale, offsetX, offsetY)
        offsetX = clamped.first
        offsetY = clamped.second
    }

    fun clearTouchSelection() {
        onPanModeChange(false)
        onFocusChange(ViewerFocus.NONE)
    }

    LaunchedEffect(entry?.id) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotation = 0f
        onPanModeChange(false)
        onFocusChange(ViewerFocus.ZOOM_IN)
    }

    LaunchedEffect(command?.nonce) {
        when (command?.type) {
            ViewerCommandType.PAN_LEFT -> {
                onPanelCollapsedChange(true)
                nudge(60f, 0f)
            }

            ViewerCommandType.PAN_RIGHT -> {
                onPanelCollapsedChange(true)
                nudge(-60f, 0f)
            }

            ViewerCommandType.PAN_UP -> {
                onPanelCollapsedChange(true)
                nudge(0f, 60f)
            }

            ViewerCommandType.PAN_DOWN -> {
                onPanelCollapsedChange(true)
                nudge(0f, -60f)
            }

            ViewerCommandType.ZOOM_IN -> applyZoom(1.2f)
            ViewerCommandType.ZOOM_OUT -> applyZoom(1f / 1.2f)

            ViewerCommandType.ROTATE_CLOCKWISE -> {
                rotateClockwise()
            }

            null -> Unit
        }
        if (command != null) onCommandConsumed()
    }

    fun viewerButtonModifier(selected: Boolean): Modifier {
        val shape = RoundedCornerShape(16.dp)
        return Modifier
            .size(width = 45.dp, height = 45.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.18f else 0.08f))
            .then(
                if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                else Modifier
            )
    }

    fun panelIconModifier(selected: Boolean): Modifier {
        val shape = RoundedCornerShape(16.dp)
        return Modifier
            .size(52.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.18f else 0.10f))
            .then(
                if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape)
                else Modifier
            )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 1.dp)
        ) {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.24f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .then(
                                    if (focus == ViewerFocus.IMAGE || panMode) {
                                        Modifier.border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.55f),
                                            RoundedCornerShape(24.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .onSizeChanged { viewportSize = it }
                                .pointerInput(entry?.id) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        clearTouchSelection()

                                        var totalDx = 0f
                                        var totalDy = 0f
                                        var maxPointerCount = 1
                                        var handled = false

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            maxPointerCount = max(maxPointerCount, pressed.size)

                                            if (pressed.isEmpty()) break
                                            if (maxPointerCount >= 2) break

                                            val tracked = event.changes.firstOrNull { it.id == down.id } ?: break
                                            val delta = tracked.positionChange()
                                            totalDx += delta.x
                                            totalDy += delta.y

                                            if (!handled && abs(totalDx) > 72f && abs(totalDx) > abs(totalDy)) {
                                                onPanelCollapsedChange(true)
                                                if (totalDx > 0f) {
                                                    if (currentIndex > 0) onPrevious()
                                                } else {
                                                    if (currentIndex < totalCount - 1) onNext()
                                                }
                                                handled = true
                                            }
                                        }
                                    }
                                }
                                .pointerInput(entry?.id) {
                                    awaitEachGesture {
                                        var previousCentroid: Offset? = null
                                        var previousDistance: Float? = null
                                        var clearedSelection = false

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }

                                            if (pressed.isEmpty()) break

                                            if (!clearedSelection && pressed.isNotEmpty()) {
                                                clearTouchSelection()
                                                clearedSelection = true
                                            }

                                            if (pressed.size < 2) {
                                                previousCentroid = null
                                                previousDistance = null
                                                continue
                                            }

                                            onPanelCollapsedChange(true)

                                            val p1 = pressed[0].position
                                            val p2 = pressed[1].position
                                            val centroid = Offset(
                                                (p1.x + p2.x) / 2f,
                                                (p1.y + p2.y) / 2f
                                            )
                                            val distance = hypot(
                                                (p1.x - p2.x).toDouble(),
                                                (p1.y - p2.y).toDouble()
                                            ).toFloat().coerceAtLeast(1f)

                                            val oldCentroid = previousCentroid
                                            val oldDistance = previousDistance

                                            if (oldCentroid != null && oldDistance != null) {
                                                val pan = centroid - oldCentroid
                                                val zoom = distance / oldDistance
                                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                                val clamped = clampOffset(
                                                    newScale,
                                                    offsetX + pan.x,
                                                    offsetY + pan.y
                                                )
                                                scale = newScale
                                                offsetX = clamped.first
                                                offsetY = clamped.second
                                            }

                                            previousCentroid = centroid
                                            previousDistance = distance
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offsetX
                                        translationY = offsetY
                                        rotationZ = rotation
                                    }
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.media_loading),
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 18.dp)
                    .then(viewerButtonModifier(focus == ViewerFocus.BACK))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            hClick()
                            onBack()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Vissza",
                    tint = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 18.dp)
                    .then(viewerButtonModifier(focus == ViewerFocus.ROTATE))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            hClick()
                            onFocusChange(ViewerFocus.ROTATE)
                            rotateClockwise()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Forgatás",
                    tint = Color.White
                )
            }

            if (!panelCollapsed) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF202020).copy(alpha = 0.95f)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                        .width(360.dp)
                        .scale(0.88f)
                        .offset(x = 70.dp, y = 45.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = viewerButtonModifier(focus == ViewerFocus.ZOOM_OUT)
                                    .weight(1f)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            hClick()
                                            applyZoom(1f / 1.2f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("–", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }

                            Box(
                                modifier = viewerButtonModifier(focus == ViewerFocus.ZOOM_IN)
                                    .weight(1f)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            hClick()
                                            applyZoom(1.2f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            }

                            Box(
                                modifier = viewerButtonModifier(focus == ViewerFocus.PREV)
                                    .weight(1f)
                                    .alpha(if (currentIndex > 0) 1f else 0.45f)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            hClick()
                                            if (currentIndex > 0) onPrevious()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("<", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }

                            Box(
                                modifier = viewerButtonModifier(focus == ViewerFocus.NEXT)
                                    .weight(1f)
                                    .alpha(if (currentIndex < totalCount - 1) 1f else 0.45f)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            hClick()
                                            if (currentIndex < totalCount - 1) onNext()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(">", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = panelIconModifier(focus == ViewerFocus.SHARE)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                hClick()
                                                onShare()
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Megosztás",
                                        tint = Color.White.copy(alpha = 0.95f)
                                    )
                                }

                                Box(
                                    modifier = panelIconModifier(focus == ViewerFocus.DELETE)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                hClick()
                                                onDelete()
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Törlés",
                                        tint = Color.White.copy(alpha = 0.95f)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry?.displayName ?: "—",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${bmp?.width ?: 0} × ${bmp?.height ?: 0}",
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = fileSizeText,
                                        color = Color.White.copy(alpha = 0.72f),
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                Text(
                                    text = dateText,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = panelIconModifier(focus == ViewerFocus.TOGGLE)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            hClick()
                                            onPanelCollapsedChange(true)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Popup bezárása",
                                    tint = Color.White.copy(alpha = 0.95f)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp)
                        .then(viewerButtonModifier(focus == ViewerFocus.TOGGLE))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                hClick()
                                onPanelCollapsedChange(false)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = "Popup kinyitása",
                        tint = Color.White
                    )
                }
            }

            if (panMode) {
                Text(
                    text = "Mozgatás mód",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.26f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
@Composable
private fun VideoPlayerDialogImmersiveEffect() {
    val view = LocalView.current
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window

    SideEffect {
        val window = dialogWindow ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}