package com.softistx.material.components.form

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import com.softistx.material.foundation.motion.MotionSpeed
import com.softistx.material.foundation.style.motion

/**
 * The M3 text fields (`TextField`, `TextareaField`, `Autocomplete`, `SelectField`).
 *
 * Empty, and that is the finding rather than an omission: M3's text field animates and dims its own
 * label, container, text and icons, so any alpha here would dim a disabled field twice. The name
 * exists so a caller can restate the default and edit it: `StxStyles.field then { … }`.
 */
val fieldStyle: Style = Style

/**
 * The disabled fade for a control M3 does not dim: the cells of an `OtpField` and the text of an
 * `InlineEdit` at rest. 0.38, so a disabled one matches a disabled button.
 */
val dimmedStyle: Style =
    Style {
        disabled { animate(motion.effects(MotionSpeed.Fast)) { alpha(DISABLED_FIELD_ALPHA) } }
    }

/**
 * What a checkbox, a switch and a radio row need and M3 has no parameter for: the press scale.
 *
 * The box, the thumb and the dot are M3's and dim themselves when disabled, so there is no alpha
 * here — the row would dim the control twice. The scale is 0.99, the value of a full-width row with its label.
 */
val toggleStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.99f) } }
    }

/**
 * The drop zone of an `UploadField`, which is ours and not M3's: a press scale of 0.99, as a card
 * has, and the disabled fade, since nothing in it dims itself.
 */
val uploadStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.99f) } }
        disabled { animate(motion.effects(MotionSpeed.Fast)) { alpha(DISABLED_FIELD_ALPHA) } }
    }

/** Material 3's disabled opacity, so a disabled field matches a disabled button. */
private const val DISABLED_FIELD_ALPHA = 0.38f
