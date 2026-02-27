@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dueboysenberry1226.px5launcher.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.media.MediaAlbum
import com.dueboysenberry1226.px5launcher.media.MediaEntry
import com.dueboysenberry1226.px5launcher.media.MediaKind
import com.dueboysenberry1226.px5launcher.media.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MediaScreen { HUB, IMAGES, VIDEOS, ALBUMS, ALBUM_CONTENT, VIEWER }

private sealed class MediaGridItem {
    data object Back : MediaGridItem()
    data class Entry(val e: MediaEntry) : MediaGridItem()
    data class Album(val a: MediaAlbum) : MediaGridItem()
}

@Composable
fun MediaRoute(
    pm: PackageManager,
    onRequestBackToGames: () -> Unit,
    hubSelectionEnabled: Boolean,
    registerKeyHandler: (((KeyEvent) -> Boolean)) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { MediaStoreRepository(context) }

    var hasPerm by remember { mutableStateOf(hasAllPermissions(context, repo)) }

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

    var viewerEntry by remember { mutableStateOf<MediaEntry?>(null) }
    val backStack = remember { mutableStateListOf<MediaScreen>() }

    fun navigateTo(next: MediaScreen) {
        if (screen != next) backStack.add(screen)
        screen = next
        selectedIndex = 0
    }

    fun goBackOneLevel() {
        if (screen == MediaScreen.VIEWER) viewerEntry = null

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
        if (screen != MediaScreen.VIEWER) {
            viewerEntry = null
        }
    }

    var refreshTick by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(hasPerm) { if (hasPerm) refreshTick++ }

    val images by produceState(initialValue = emptyList<MediaEntry>(), refreshTick) {
        value = if (!hasPerm) emptyList() else withContext(Dispatchers.Default) { repo.loadImages() }
    }
    val videos by produceState(initialValue = emptyList<MediaEntry>(), refreshTick) {
        value = if (!hasPerm) emptyList() else withContext(Dispatchers.Default) { repo.loadVideos() }
    }
    val albums by produceState(initialValue = emptyList<MediaAlbum>(), refreshTick) {
        value = if (!hasPerm) emptyList() else withContext(Dispatchers.Default) { repo.loadAlbums() }
    }
    val albumContent by produceState(initialValue = emptyList<MediaEntry>(), refreshTick, currentAlbumId) {
        val bid = currentAlbumId
        value =
            if (!hasPerm || bid.isNullOrBlank()) emptyList()
            else withContext(Dispatchers.Default) { repo.loadAlbumContent(bid) }
    }

    val columns = 6

    fun openViewer(e: MediaEntry) {
        viewerEntry = e
        navigateTo(MediaScreen.VIEWER)
    }

    fun openVideoExternal(uri: Uri) {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*") // nem UI szöveg, maradhat
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (i.resolveActivity(context.packageManager) != null) context.startActivity(i)
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
                selectedIndex = if (last >= 0) lastContentSelectedIndex.coerceIn(0, last) else 0
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
            if (action != AndroidKeyEvent.ACTION_UP) return@internalKeyHandler false
        } else {
            if (action != AndroidKeyEvent.ACTION_DOWN) return@internalKeyHandler false
        }

        if (!hubSelectionEnabled && screen == MediaScreen.HUB && code == AndroidKeyEvent.KEYCODE_DPAD_UP) {
            return@internalKeyHandler false
        }

        if (!hasPerm) {
            return@internalKeyHandler when (code) {
                in backCodes -> {
                    onRequestBackToGames()
                    true
                }
                in okCodes -> {
                    permLauncher.launch(MediaStoreRepository.requiredPermissions())
                    true
                }
                else -> false
            }
        }

        fun ensureSelectionForGrid() {
            if (selectedIndex == -1) {
                val last = gridItems.lastIndex
                selectedIndex = if (last >= 0) lastContentSelectedIndex.coerceIn(0, last) else 0
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
                                if (it.e.kind == MediaKind.VIDEO) openVideoExternal(it.e.uri)
                                else openViewer(it.e)
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
                when (code) {
                    in backCodes, in okCodes -> {
                        goBackOneLevel()
                        true
                    }
                    else -> true
                }
            }
        }
    }

    SideEffect {
        registerKeyHandler(internalKeyHandler)
    }

    Box(Modifier.fillMaxWidth()) {
        if (!hasPerm) {
            MediaPermissionCard(
                onRequest = { permLauncher.launch(MediaStoreRepository.requiredPermissions()) },
                onBack = onRequestBackToGames
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
                    onOpenAlbums = { navigateTo(MediaScreen.ALBUMS) }
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
                    onOpenAlbum = null
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
                    onOpenEntry = { e -> openVideoExternal(e.uri) },
                    onOpenAlbum = null
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
                    }
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
                        if (e.kind == MediaKind.VIDEO) openVideoExternal(e.uri) else openViewer(e)
                    },
                    onOpenAlbum = null
                )
            }

            MediaScreen.VIEWER -> {
                MediaImageViewer(entry = viewerEntry, onClose = { goBackOneLevel() })
            }
        }
    }
}

private fun hasAllPermissions(ctx: android.content.Context, repo: MediaStoreRepository): Boolean {
    val perms = MediaStoreRepository.requiredPermissions()
    return perms.all { p ->
        ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun MediaPermissionCard(
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
                TouchTextButton(text = stringResource(R.string.media_perm_request), onClick = onRequest)
                TouchTextButton(text = stringResource(R.string.common_back), onClick = onBack, alpha = 0.85f)
            }
        }
    }
}

@Composable
private fun TouchTextButton(
    text: String,
    onClick: () -> Unit,
    alpha: Float = 1f
) {
    Text(
        text = text,
        color = Color.White.copy(alpha = alpha),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 10.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun MediaHub(
    selectedIndex: Int,
    hubSelectionEnabled: Boolean,
    onSelect: (Int) -> Unit,
    onOpenImages: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenAlbums: () -> Unit
) {
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
                    onSelect(0)
                    onOpenImages()
                }
            )
            MediaBigButton(
                icon = "🎬",
                text = stringResource(R.string.media_title_videos),
                selected = hubSelectionEnabled && selectedIndex == 1,
                onClick = {
                    onSelect(1)
                    onOpenVideos()
                }
            )
            MediaBigButton(
                icon = "📁",
                text = stringResource(R.string.media_title_albums),
                selected = hubSelectionEnabled && selectedIndex == 2,
                onClick = {
                    onSelect(2)
                    onOpenAlbums()
                }
            )
        }
    }
}

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
            .then(if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape) else Modifier)
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
            Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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
    onOpenAlbum: ((MediaAlbum) -> Unit)?
) {
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
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
        )

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { i, item ->
                val isSel = (selectedIndex >= 0 && i == selectedIndex)

                fun selectAnd(block: () -> Unit) {
                    onSelectChange(i)
                    block()
                }

                when (item) {
                    is MediaGridItem.Back -> BackTile(
                        selected = isSel,
                        label = stringResource(R.string.common_back),
                        onClick = { selectAnd { onBack() } }
                    )

                    is MediaGridItem.Entry -> MediaThumbTile(
                        selected = isSel,
                        entry = item.e,
                        onClick = { selectAnd { onOpenEntry?.invoke(item.e) } }
                    )

                    is MediaGridItem.Album -> AlbumTile(
                        selected = isSel,
                        album = item.a,
                        onClick = { selectAnd { onOpenAlbum?.invoke(item.a) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackTile(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(if (selected) 1.06f else 1.0f, label = "mediaBackScale")
    val bgAlpha = if (selected) 0.11f else 0.06f

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = bgAlpha)),
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth()
            .scale(scale)
            .then(if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape) else Modifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = stringResource(R.string.media_back_tile, label),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AlbumTile(selected: Boolean, album: MediaAlbum, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(if (selected) 1.06f else 1.0f, label = "albumScale")
    val bgAlpha = if (selected) 0.11f else 0.06f

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = bgAlpha)),
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth()
            .scale(scale)
            .then(if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape) else Modifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) { Text("📁", fontSize = 18.sp) }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    album.bucketName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.common_open),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MediaThumbTile(
    selected: Boolean,
    entry: MediaEntry,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1.0f,
        label = "mediaTileScale"
    )
    val bgAlpha = if (selected) 0.11f else 0.06f

    val thumb: Bitmap? by produceState<Bitmap?>(initialValue = null, entry.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(entry.uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = bgAlpha)),
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth()
            .scale(scale)
            .then(if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.95f), shape) else Modifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress?.invoke() }
            )
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            if (thumb != null) {
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (entry.kind == MediaKind.VIDEO) "🎬" else "🖼️", fontSize = 22.sp)
                }
            }

            if (entry.kind == MediaKind.VIDEO) {
                Text(
                    "▶",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaImageViewer(
    entry: MediaEntry?,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val bmp: Bitmap? by produceState<Bitmap?>(initialValue = null, entry?.uri) {
        val u = entry?.uri ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(u)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )
        } else {
            Text(
                text = stringResource(R.string.media_loading),
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Text(
            text = stringResource(R.string.homescreen_close),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
        )
    }
}