package com.strange.material.display

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.selected
import androidx.compose.ui.unit.dp
import com.strange.material.theme.motion
import com.strange.material.theme.radii
import com.strange.material.theme.scheme
import com.strange.material.theme.spacing

/**
 * The chip's whole appearance, selection included. Selection is a style state rather than a branch
 * in the composable, which is what makes the transition between the two animate itself.
 */
val chipStyle: Style =
    Style {
        background(scheme.surfaceContainerHigh)
        contentColor(scheme.onSurfaceVariant)
        border(1.dp, scheme.outlineVariant)
        shape(RoundedCornerShape(radii.full))
        contentPaddingHorizontal(spacing.sm)
        contentPaddingVertical(spacing.xs)
        selected {
            animate {
                background(scheme.secondaryContainer)
                contentColor(scheme.onSecondaryContainer)
                borderColor(scheme.secondary)
            }
        }
        hovered { animate { background(scheme.surfaceContainerHighest) } }
        pressed { animate(motion.spec(motion.instant)) { scale(0.97f) } }
    }
