package com.softistx.material.feedback

import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The expressive loading mark. Material 3's `LoadingIndicator` — the morphing polygon, not the
 * circular spinner [Progress] already wraps.
 *
 * Use this when the wait *is* the content (first paint of a screen). Use [Progress] when the wait
 * is attached to a control.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    if (progress == null) {
        LoadingIndicator(modifier = modifier)
    } else {
        LoadingIndicator(progress = { progress }, modifier = modifier)
    }
}
