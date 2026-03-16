@file:OptIn(ExperimentalFoundationApi::class)

package com.dueboysenberry1226.px5launcher.ui.phone

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class WallpaperPickerResult(
    val imageUri: String,
    val userScale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val applyHome: Boolean,
    val applyLock: Boolean
)

private data class WallpaperEditorState(
    val imageUri: String? = null,
    val userScale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val applyHome: Boolean = true,
    val applyLock: Boolean = false
)

private val WallpaperEditorStateSaver: Saver<WallpaperEditorState, Any> = listSaver(
    save = {
        listOf(
            it.imageUri ?: "",
            it.userScale,
            it.offsetX,
            it.offsetY,
            it.applyHome,
            it.applyLock
        )
    },
    restore = {
        WallpaperEditorState(
            imageUri = (it[0] as String).ifBlank { null },
            userScale = it[1] as Float,
            offsetX = it[2] as Float,
            offsetY = it[3] as Float,
            applyHome = it[4] as Boolean,
            applyLock = it[5] as Boolean
        )
    }
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun WallpaperPickerScreen(
    initialImageUri: String? = null,
    initialUserScale: Float = 1f,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    initialApplyHome: Boolean = true,
    initialApplyLock: Boolean = false,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onApply: (WallpaperPickerResult) -> Unit
) {
    val context = LocalContext.current

    var editorState by rememberSaveable(stateSaver = WallpaperEditorStateSaver) {
        mutableStateOf(
            WallpaperEditorState(
                imageUri = initialImageUri,
                userScale = initialUserScale,
                offsetX = initialOffsetX,
                offsetY = initialOffsetY,
                applyHome = initialApplyHome,
                applyLock = initialApplyLock
            )
        )
    }

    var showApplyDialog by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            editorState = editorState.copy(
                imageUri = uri.toString(),
                userScale = 1f,
                offsetX = 0f,
                offsetY = 0f
            )
        }
    }

    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, editorState.imageUri) {
        val current = editorState.imageUri
        value = if (current.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                loadImageBitmapSafely(context, Uri.parse(current))
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050814),
                        Color(0xFF09101D),
                        Color(0xFF060B13)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val isCompactHeight = maxHeight < 780.dp
        val sidePadding = 18.dp
        val topSpacing = if (isCompactHeight) 12.dp else 16.dp
        val previewFillHeight = if (isCompactHeight) 0.50f else 0.58f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sidePadding, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Háttérkép beállítása",
                color = Color.White.copy(alpha = 0.96f),
                fontSize = if (isCompactHeight) 24.sp else 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (editorState.imageUri == null) {
                    "Érintsd meg az előnézetet a kép kiválasztásához."
                } else {
                    "Nagyíts és húzd a képet úgy, ahogy a kezdőképernyőn látni szeretnéd."
                },
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.widthIn(max = 420.dp)
            )

            Spacer(modifier = Modifier.height(topSpacing))

            GlassSectionCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = if (isCompactHeight) 16.dp else 20.dp,
                            vertical = if (isCompactHeight) 16.dp else 20.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PhoneWallpaperPreview(
                        imageBitmap = imageBitmap,
                        userScale = editorState.userScale,
                        offsetX = editorState.offsetX,
                        offsetY = editorState.offsetY,
                        previewHeightFraction = previewFillHeight,
                        onPreviewClick = {
                            pickerLauncher.launch(arrayOf("image/*"))
                        },
                        onDeleteClick = {
                            editorState = editorState.copy(
                                imageUri = null,
                                userScale = 1f,
                                offsetX = 0f,
                                offsetY = 0f,
                                applyHome = true,
                                applyLock = false
                            )
                            onClear()
                        },
                        onTransform = { newScale, newOffsetX, newOffsetY ->
                            editorState = editorState.copy(
                                userScale = newScale,
                                offsetX = newOffsetX,
                                offsetY = newOffsetY
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassActionButton(
                    text = "Mégse",
                    modifier = Modifier.weight(1f)
                ) {
                    onCancel()
                }

                GlassActionButton(
                    text = "Beállítás",
                    emphasized = true,
                    enabled = editorState.imageUri != null,
                    modifier = Modifier.weight(1f)
                ) {
                    if (editorState.imageUri != null) {
                        showApplyDialog = true
                    }
                }
            }
        }
    }

    if (showApplyDialog) {
        ApplyWallpaperDialog(
            applyHome = editorState.applyHome,
            applyLock = editorState.applyLock,
            canApply = editorState.imageUri != null && (editorState.applyHome || editorState.applyLock),
            onDismiss = {
                showApplyDialog = false
            },
            onToggleHome = {
                editorState = editorState.copy(applyHome = !editorState.applyHome)
            },
            onToggleLock = {
                editorState = editorState.copy(applyLock = !editorState.applyLock)
            },
            onApplyClick = {
                val uri = editorState.imageUri ?: return@ApplyWallpaperDialog
                showApplyDialog = false
                onApply(
                    WallpaperPickerResult(
                        imageUri = uri,
                        userScale = editorState.userScale,
                        offsetX = editorState.offsetX,
                        offsetY = editorState.offsetY,
                        applyHome = editorState.applyHome,
                        applyLock = editorState.applyLock
                    )
                )
            }
        )
    }
}

@Composable
private fun GlassSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(30.dp),
        modifier = modifier.border(
            width = 1.5.dp,
            color = Color.White.copy(alpha = 0.12f),
            shape = RoundedCornerShape(30.dp)
        )
    ) {
        content()
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun PhoneWallpaperPreview(
    imageBitmap: ImageBitmap?,
    userScale: Float,
    offsetX: Float,
    offsetY: Float,
    previewHeightFraction: Float,
    onPreviewClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTransform: (userScale: Float, offsetX: Float, offsetY: Float) -> Unit
) {
    val previewShape = RoundedCornerShape(24.dp)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val previewWidth = (maxWidth * 0.56f).coerceAtMost(340.dp)

        Box(
            modifier = Modifier
                .widthIn(max = previewWidth)
                .fillMaxWidth()
        ) {
            val previewModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 19.5f)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = previewShape
                )
                .then(
                    if (imageBitmap == null) {
                        Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPreviewClick,
                            onLongClick = onPreviewClick
                        )
                    } else {
                        Modifier
                    }
                )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0A0E17).copy(alpha = 0.95f)
                ),
                shape = previewShape,
                modifier = previewModifier
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(previewShape)
                        .background(Color(0xFF0B1020)),
                    contentAlignment = Alignment.Center
                ) {
                    val frameWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val frameHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                    if (imageBitmap == null) {
                        EmptyWallpaperPreview()
                        return@BoxWithConstraints
                    }

                    val bitmapWidth = imageBitmap.width.toFloat().coerceAtLeast(1f)
                    val bitmapHeight = imageBitmap.height.toFloat().coerceAtLeast(1f)

                    val baseFillScale = max(
                        frameWidth / bitmapWidth,
                        frameHeight / bitmapHeight
                    )

                    val currentUserScale = userScale.coerceIn(1f, 6f)
                    val effectiveScale = baseFillScale * currentUserScale

                    val drawnWidth = bitmapWidth * effectiveScale
                    val drawnHeight = bitmapHeight * effectiveScale

                    val maxOffsetX = ((drawnWidth - frameWidth) / 2f).coerceAtLeast(0f)
                    val maxOffsetY = ((drawnHeight - frameHeight) / 2f).coerceAtLeast(0f)

                    val normalizedOffsetX = offsetX.coerceIn(-1f, 1f)
                    val normalizedOffsetY = offsetY.coerceIn(-1f, 1f)

                    val appliedOffsetX = normalizedOffsetX * maxOffsetX
                    val appliedOffsetY = normalizedOffsetY * maxOffsetY

                    LaunchedEffect(offsetX, offsetY) {
                        val clampedX = offsetX.coerceIn(-1f, 1f)
                        val clampedY = offsetY.coerceIn(-1f, 1f)
                        if (clampedX != offsetX || clampedY != offsetY) {
                            onTransform(currentUserScale, clampedX, clampedY)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(imageBitmap, userScale, offsetX, offsetY) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextUserScale = (currentUserScale * zoom).coerceIn(1f, 6f)
                                    val nextEffectiveScale = baseFillScale * nextUserScale

                                    val nextWidth = bitmapWidth * nextEffectiveScale
                                    val nextHeight = bitmapHeight * nextEffectiveScale

                                    val nextMaxOffsetX =
                                        ((nextWidth - frameWidth) / 2f).coerceAtLeast(0f)
                                    val nextMaxOffsetY =
                                        ((nextHeight - frameHeight) / 2f).coerceAtLeast(0f)

                                    val nextAppliedOffsetX =
                                        (appliedOffsetX + pan.x).coerceIn(-nextMaxOffsetX, nextMaxOffsetX)
                                    val nextAppliedOffsetY =
                                        (appliedOffsetY + pan.y).coerceIn(-nextMaxOffsetY, nextMaxOffsetY)

                                    val nextNormalizedOffsetX =
                                        if (nextMaxOffsetX > 0f) {
                                            (nextAppliedOffsetX / nextMaxOffsetX).coerceIn(-1f, 1f)
                                        } else {
                                            0f
                                        }

                                    val nextNormalizedOffsetY =
                                        if (nextMaxOffsetY > 0f) {
                                            (nextAppliedOffsetY / nextMaxOffsetY).coerceIn(-1f, 1f)
                                        } else {
                                            0f
                                        }

                                    onTransform(
                                        nextUserScale,
                                        nextNormalizedOffsetX,
                                        nextNormalizedOffsetY
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = currentUserScale
                                    scaleY = currentUserScale
                                    translationX = appliedOffsetX
                                    translationY = appliedOffsetY
                                }
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.06f))
                        )

                        PhoneFrameOverlay()
                    }
                }
            }

            if (imageBitmap != null) {
                SmallIconGlassButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp),
                    onClick = onDeleteClick
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Törlés",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWallpaperPreview() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 22.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(34.dp)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.75f),
                        radius = size.minDimension * 0.17f,
                        center = center.copy(
                            x = center.x - size.width * 0.12f,
                            y = center.y - size.height * 0.08f
                        )
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.75f),
                        start = Offset(size.width * 0.20f, size.height * 0.78f),
                        end = Offset(size.width * 0.44f, size.height * 0.52f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.75f),
                        start = Offset(size.width * 0.44f, size.height * 0.52f),
                        end = Offset(size.width * 0.64f, size.height * 0.68f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.75f),
                        start = Offset(size.width * 0.64f, size.height * 0.68f),
                        end = Offset(size.width * 0.82f, size.height * 0.34f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Nincs kiválasztott kép",
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Érintsd meg ezt az előnézetet a kép kiválasztásához.",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun PhoneFrameOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(24.dp)
            )
    )
}

@Composable
private fun ApplyWallpaperDialog(
    applyHome: Boolean,
    applyLock: Boolean,
    canApply: Boolean,
    onDismiss: () -> Unit,
    onToggleHome: () -> Unit,
    onToggleLock: () -> Unit,
    onApplyClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF101827).copy(alpha = 0.96f)
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.4.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "Beállítás mint",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelectChip(
                        text = "Kezdő képernyő",
                        selected = applyHome,
                        modifier = Modifier.weight(1f),
                        onClick = onToggleHome
                    )

                    SelectChip(
                        text = "Zárolt képernyő",
                        selected = applyLock,
                        modifier = Modifier.weight(1f),
                        onClick = onToggleLock
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassActionButton(
                        text = "Bezárás",
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )

                    GlassActionButton(
                        text = "Alkalmaz",
                        emphasized = true,
                        enabled = canApply,
                        modifier = Modifier.weight(1f),
                        onClick = onApplyClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color.White.copy(alpha = 0.07f)
    }

    val border = if (selected) {
        Color.White.copy(alpha = 0.42f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .border(1.4.dp, border, RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White.copy(alpha = if (selected) 0.96f else 0.78f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GlassActionButton(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = when {
        !enabled -> Color.White.copy(alpha = 0.05f)
        emphasized -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val border = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        emphasized -> Color.White.copy(alpha = 0.32f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .border(1.3.dp, border, RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (enabled) onClick()
                },
                onLongClick = {
                    if (enabled) onClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White.copy(alpha = if (enabled) 0.94f else 0.38f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SmallIconGlassButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.34f)
        ),
        shape = CircleShape,
        modifier = modifier
            .size(38.dp)
            .border(
                width = 1.2.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = CircleShape
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private fun loadImageBitmapSafely(
    context: android.content.Context,
    uri: Uri
): ImageBitmap? {
    return runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val srcW = info.size.width.coerceAtLeast(1)
                val srcH = info.size.height.coerceAtLeast(1)
                val longest = max(srcW, srcH)
                if (longest > 4096) {
                    val factor = longest / 4096f
                    decoder.setTargetSize(
                        (srcW / factor).toInt().coerceAtLeast(1),
                        (srcH / factor).toInt().coerceAtLeast(1)
                    )
                }
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream)
                original?.let { scaleBitmapDownIfNeeded(it) }
            }
        } ?: return null

        bitmap.asImageBitmap()
    }.getOrNull()
}

private fun scaleBitmapDownIfNeeded(bitmap: Bitmap): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= 4096) return bitmap

    val factor = longest / 4096f
    val targetW = (bitmap.width / factor).toInt().coerceAtLeast(1)
    val targetH = (bitmap.height / factor).toInt().coerceAtLeast(1)

    return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
}