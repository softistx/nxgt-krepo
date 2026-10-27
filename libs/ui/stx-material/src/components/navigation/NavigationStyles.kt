package com.softistx.material.components.navigation

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.then
import com.softistx.material.components.button.DISABLED_ALPHA
import com.softistx.material.foundation.style.compactPress
import com.softistx.material.foundation.style.motion

/**
 * What Material 3 has no parameter for on the chrome we wrap: the press giving under the finger.
 *
 * Colour, indicator, container and shape stay on M3's `*Defaults`. A `background()` here would
 * paint over the tab or the rail item M3 already painted.
 */
val navigationItemStyle: Style =
    compactPress then
        Style {
            disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
        }

/** A trail segment is a quiet control; the press is the only state it owns. */
val breadcrumbStyle: Style = navigationItemStyle

/** A completed step is the one that can be pressed to go back. */
val stepperStyle: Style = navigationItemStyle
