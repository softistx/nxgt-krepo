package com.softistx.material.navigation

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import com.softistx.material.button.DISABLED_ALPHA
import com.softistx.material.motion.MotionSpeed
import com.softistx.material.theme.motion

/**
 * What Material 3 has no parameter for on the chrome we wrap: the press giving under the finger.
 *
 * Colour, indicator, container and shape stay on M3's `*Defaults`. A `background()` here would
 * paint over the tab or the rail item M3 already painted.
 */
val navigationItemStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.97f) } }
        disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
    }

/** A trail segment is a quiet control; the press is the only state it owns. */
val breadcrumbStyle: Style = navigationItemStyle

/** A completed step is the one that can be pressed to go back. */
val stepperStyle: Style = navigationItemStyle
