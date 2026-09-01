package com.softistx.material.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.softistx.material.theme.StrangeTheme

/**
 * Chrome around a video, a PDF page or a camera preview.
 *
 * The library does not take a decoder, a PDF renderer or CameraX onto every consumer of `Button`.
 * [content] is the renderer the host already has; this is the frame, the ratio and the overlay.
 */
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    ratio: Float = 16f / 9f,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    MediaFrame(modifier = modifier, ratio = ratio, overlay = overlay, content = content)
}

@Composable
fun PdfSurface(
    modifier: Modifier = Modifier,
    ratio: Float = 1f / 1.414f,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    MediaFrame(modifier = modifier, ratio = ratio, overlay = overlay, content = content)
}

@Composable
fun CameraSurface(
    modifier: Modifier = Modifier,
    ratio: Float = 3f / 4f,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    MediaFrame(modifier = modifier, ratio = ratio, overlay = overlay, content = content)
}

@Composable
private fun MediaFrame(
    modifier: Modifier,
    ratio: Float,
    overlay: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(ratio)
                .clip(MaterialTheme.shapes.large)
                .background(StrangeTheme.colors.scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize(), content = content)
        overlay()
    }
}
