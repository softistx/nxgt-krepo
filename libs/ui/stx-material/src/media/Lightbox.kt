package com.softistx.material.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.softistx.material.button.ButtonColor
import com.softistx.material.button.ButtonVariant
import com.softistx.material.button.IconButton
import com.softistx.material.icon.StxIcons
import com.softistx.material.theme.StxTheme

/**
 * One image, full frame, with a way out and a way to the neighbours.
 *
 * Not composed when [visible] is false. Keyboard handling is the host's — this is the chrome.
 */
@Composable
fun Lightbox(
    visible: Boolean,
    model: Any?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    description: String? = null,
) {
    if (!visible) return
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)),
    ) {
        StxImage(
            model = model,
            description = description,
            modifier = Modifier.fillMaxSize().padding(StxTheme.spacing.xl),
            contentScale = ContentScale.Fit,
        )
        IconButton(
            icon = StxIcons.Close,
            description = "Close",
            onClick = onDismiss,
            variant = ButtonVariant.Filled,
            color = ButtonColor.Neutral,
            modifier = Modifier.align(Alignment.TopEnd).padding(StxTheme.spacing.md),
        )
        if (onPrevious != null) {
            IconButton(
                icon = StxIcons.ChevronLeft,
                description = "Previous",
                onClick = onPrevious,
                variant = ButtonVariant.Filled,
                color = ButtonColor.Neutral,
                modifier = Modifier.align(Alignment.CenterStart).padding(StxTheme.spacing.md),
            )
        }
        if (onNext != null) {
            IconButton(
                icon = StxIcons.ChevronRight,
                description = "Next",
                onClick = onNext,
                variant = ButtonVariant.Filled,
                color = ButtonColor.Neutral,
                modifier = Modifier.align(Alignment.CenterEnd).padding(StxTheme.spacing.md),
            )
        }
    }
}
