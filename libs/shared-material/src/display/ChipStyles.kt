package com.strange.material.display

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.pressed
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.strange.material.motion.MotionSpeed
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.motion

/**
 * The hover state Material 3's chip does not have.
 *
 * `SelectableChipColors` carries thirteen colours and not one of them is a hover: M3's own source
 * says so, in a `TODO(…): Support other states: hover, focus, drag` sitting in the constructor. On
 * a phone that is invisible; on desktop the pointer crosses a chip and nothing answers.
 *
 * So the state layer is added here, the way M3 would have added it — a tint composited over
 * whichever container the chip is already wearing, so a selected chip answers as well as an
 * unselected one, and handed back as M3's own [SelectableChipColors] so M3 still does the painting.
 *
 * It fades on the *effects* axis. A container colour that overshoots its own tint reads as a
 * flicker, and effects is the axis that lands exactly where it was sent.
 */
@Composable
fun chipColors(hovered: Boolean): SelectableChipColors {
    val scheme = MaterialTheme.colorScheme
    val motion = StrangeTheme.motion
    val tint = scheme.onSurface.copy(alpha = HOVER_STATE_LAYER_ALPHA)
    val container by animateColorAsState(
        targetValue = if (hovered) tint else Color.Transparent,
        animationSpec = motion.effects(),
        label = "chipContainer",
    )
    val selectedContainer by animateColorAsState(
        targetValue =
            if (hovered) tint.compositeOver(scheme.secondaryContainer) else scheme.secondaryContainer,
        animationSpec = motion.effects(),
        label = "chipSelectedContainer",
    )
    return FilterChipDefaults.filterChipColors(
        containerColor = container,
        selectedContainerColor = selectedContainer,
    )
}

/**
 * What Material 3's `FilterChip` does not do on its own, and cannot be told to.
 *
 * Selection, the border and the disabled treatment are all M3's — a chip was the clearest case of a
 * style block duplicating work the component already did, and painting a `background` over a
 * `FilterChip` hid its selected container entirely. The press scale is what is left.
 */
val chipStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.97f) } }
    }

/** Material's state-layer opacity for a hovered surface. */
private const val HOVER_STATE_LAYER_ALPHA = 0.08f
