package com.strange.material.form

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.disabled
import com.strange.material.motion.MotionSpeed
import com.strange.material.theme.motion

/**
 * What Material 3's inputs have no parameter for.
 *
 * Which is very little, and that is the finding rather than an omission: M3's text field animates
 * its own label, container and error colours, and its checkbox, switch and slider each animate
 * their own thumb. Painting over any of them would replace an animation with a worse one. The
 * disabled fade is what is left — M3 dims its own colours but not a leading icon or a label the
 * caller has slotted in.
 */
val fieldStyle: Style =
    Style {
        disabled { animate(motion.effects(MotionSpeed.Fast)) { alpha(DISABLED_FIELD_ALPHA) } }
    }

/** Material 3's disabled opacity, so a disabled field matches a disabled button. */
private const val DISABLED_FIELD_ALPHA = 0.38f
