package com.softistx.material.components.feedback

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The expressive loading mark. Material 3's `LoadingIndicator` — the morphing polygon, not the
 * circular spinner [Progress] already wraps.
 *
 * Use this when the wait *is* the content (first paint of a screen). Use [Progress] when the wait
 * is attached to a control.
 *
 * [contained] seats the polygon on a `primaryContainer` disc (M3's `ContainedLoadingIndicator`),
 * which keeps it legible over an image or a busy surface; the bare mark is `primary` ink straight
 * on whatever is behind it. [progress] makes it determinate in both forms.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    contained: Boolean = false,
) {
    if (progress == null) {
        if (contained) ContainedLoadingIndicator(modifier = modifier) else LoadingIndicator(modifier = modifier)
    } else if (contained) {
        ContainedLoadingIndicator(progress = { progress }, modifier = modifier)
    } else {
        LoadingIndicator(progress = { progress }, modifier = modifier)
    }
}
