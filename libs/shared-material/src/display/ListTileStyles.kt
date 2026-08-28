package com.strange.material.display

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.hovered
import androidx.compose.ui.unit.dp
import com.strange.material.theme.radii
import com.strange.material.theme.scheme
import com.strange.material.theme.spacing

/**
 * A tile only lights up when it does something, so the hover state is declared conditionally
 * rather than always present and sometimes pointless.
 */
fun listTileStyle(interactive: Boolean): Style =
    Style {
        shape(RoundedCornerShape(radii.md))
        contentPaddingHorizontal(spacing.md)
        contentPaddingVertical(spacing.sm)
        minHeight(TouchTarget)
        if (interactive) {
            hovered { animate { background(scheme.surfaceContainerHigh) } }
        }
    }

/** Material's two-line list item height, which is also comfortably past the 48 dp touch target. */
private val TouchTarget = 56.dp
