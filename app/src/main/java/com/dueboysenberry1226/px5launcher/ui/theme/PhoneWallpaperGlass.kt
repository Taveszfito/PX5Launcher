package com.dueboysenberry1226.px5launcher.ui.theme

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class PhoneWallpaperGlassState(
    val imageBitmap: ImageBitmap?,
    val hasWallpaper: Boolean,
    val rootSizePx: IntSize,
    val userScale: Float,
    val offsetX: Float,
    val offsetY: Float
)

val LocalPhoneWallpaperGlassState = staticCompositionLocalOf {
    PhoneWallpaperGlassState(
        imageBitmap = null,
        hasWallpaper = false,
        rootSizePx = IntSize.Zero,
        userScale = 1f,
        offsetX = 0f,
        offsetY = 0f
    )
}

@Composable
fun ProvidePhoneWallpaperGlassState(
    state: PhoneWallpaperGlassState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalPhoneWallpaperGlassState provides state,
        content = content
    )
}

@Composable
fun WallpaperGlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    baseContainerColor: Color,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    enableBlur: Boolean,
    blurRadiusPx: Float,
    dimAlpha: Float,
    overlayAlpha: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalPhoneWallpaperGlassState.current

    val topLeftState = remember { mutableStateOf(Offset.Zero) }
    val sizeState = remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { coords ->
                topLeftState.value = coords.positionInRoot()
                sizeState.value = coords.size
            }
    ) {
        val imageBitmap = glass.imageBitmap
        val rootSizePx = glass.rootSizePx
        val topLeft = topLeftState.value
        val cardSize = sizeState.value

        val canDrawWallpaperBlur =
            enableBlur &&
                    blurRadiusPx > 0f &&
                    glass.hasWallpaper &&
                    imageBitmap != null &&
                    rootSizePx.width > 0 &&
                    rootSizePx.height > 0 &&
                    cardSize.width > 0 &&
                    cardSize.height > 0

        if (canDrawWallpaperBlur) {
            val rootWidth = rootSizePx.width.toFloat().coerceAtLeast(1f)
            val rootHeight = rootSizePx.height.toFloat().coerceAtLeast(1f)

            val bitmapWidth = imageBitmap.width.toFloat().coerceAtLeast(1f)
            val bitmapHeight = imageBitmap.height.toFloat().coerceAtLeast(1f)

            val baseFillScale = max(
                rootWidth / bitmapWidth,
                rootHeight / bitmapHeight
            )

            val currentUserScale = glass.userScale.coerceIn(1f, 6f)
            val drawnWidth = bitmapWidth * baseFillScale * currentUserScale
            val drawnHeight = bitmapHeight * baseFillScale * currentUserScale

            val maxOffsetX = ((drawnWidth - rootWidth) / 2f).coerceAtLeast(0f)
            val maxOffsetY = ((drawnHeight - rootHeight) / 2f).coerceAtLeast(0f)

            val appliedOffsetX = glass.offsetX.coerceIn(-1f, 1f) * maxOffsetX
            val appliedOffsetY = glass.offsetY.coerceIn(-1f, 1f) * maxOffsetY

            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(rootSizePx.width.dp, rootSizePx.height.dp)
                    .graphicsLayer {
                        translationX = appliedOffsetX - topLeft.x
                        translationY = appliedOffsetY - topLeft.y
                        scaleX = currentUserScale
                        scaleY = currentUserScale

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = AndroidRenderEffect.createBlurEffect(
                                blurRadiusPx,
                                blurRadiusPx,
                                Shader.TileMode.DECAL
                            ).asComposeRenderEffect()
                        }
                    }
            )
        }

        if (overlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = overlayAlpha))
            )
        }

        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(baseContainerColor)
                .then(
                    if (borderColor.alpha > 0f && borderWidth > 0.dp) {
                        Modifier.border(borderWidth, borderColor, shape)
                    } else {
                        Modifier
                    }
                )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(contentPadding),
            content = content
        )
    }
}