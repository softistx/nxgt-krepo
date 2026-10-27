package com.softistx.material.components.form

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.scale
import androidx.compose.foundation.style.then
import com.softistx.material.components.button.DISABLED_ALPHA
import com.softistx.material.foundation.motion.MotionSpeed
import com.softistx.material.foundation.style.motion
import com.softistx.material.foundation.style.rowPress

/**
 * The disabled fade for a control M3 does not dim: the cells of an `OtpField` and the text of an
 * `InlineEdit` at rest. 0.38, so a disabled one matches a disabled button.
 */
internal val dimmedStyle: Style =
    Style {
        disabled { animate(motion.effects(MotionSpeed.Fast)) { alpha(DISABLED_ALPHA) } }
    }

/**
 * What a checkbox, a switch and a radio row need and M3 has no parameter for: the press scale.
 *
 * The box, the thumb and the dot are M3's and dim themselves when disabled, so there is no alpha
 * here — the row would dim the control twice. The scale is 0.99, the value of a full-width row with its label.
 */
internal val toggleStyle: Style = rowPress

/**
 * The drop zone of an `UploadField`, which is ours and not M3's: a press scale of 0.99, as a card
 * has, and the disabled fade, since nothing in it dims itself.
 */
internal val uploadStyle: Style =
    rowPress then
        Style {
            disabled { animate(motion.effects(MotionSpeed.Fast)) { alpha(DISABLED_ALPHA) } }
        }
