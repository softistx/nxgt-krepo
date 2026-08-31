package com.strange.material.feedback

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class ProgressKind {
    Linear,
    Circular,
}

/**
 * Progress, determinate or not. Material 3's linear and circular indicators.
 *
 * Pass [progress] in 0..1 for a known amount; leave it null for an unspecified wait. One
 * composable, so a screen does not have to swap names when it learns how far along it is.
 */
@Composable
fun Progress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    kind: ProgressKind = ProgressKind.Linear,
) {
    when (kind) {
        ProgressKind.Linear -> {
            if (progress == null) {
                LinearProgressIndicator(modifier = modifier)
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = modifier)
            }
        }

        ProgressKind.Circular -> {
            if (progress == null) {
                CircularProgressIndicator(modifier = modifier)
            } else {
                CircularProgressIndicator(progress = { progress }, modifier = modifier)
            }
        }
    }
}
