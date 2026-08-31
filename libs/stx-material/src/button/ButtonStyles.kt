package com.strange.material.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.animate
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.scale
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.strange.material.motion.MotionSpeed
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone
import com.strange.material.theme.ToneColors
import com.strange.material.theme.motion

/**
 * The 5 × 7 matrix, resolved once, as Material 3's own [ButtonColors].
 *
 * M3 paints the button — container, content, ripple, disabled treatment — so the matrix has to
 * arrive in the shape M3 accepts. Adding a colour touches one enum entry and one `when` branch,
 * which is the point of resolving it in a single place; painting it ourselves would have thrown
 * away the ripple and the disabled alpha M3 already gets right.
 */
@Composable
fun buttonColors(
    variant: ButtonVariant,
    color: ButtonColor,
): ButtonColors {
    val tone = buttonTone(color)
    val scheme = MaterialTheme.colorScheme
    return when (variant) {
        ButtonVariant.Filled -> {
            ButtonDefaults.buttonColors(
                containerColor = tone.main,
                contentColor = tone.onMain,
            )
        }

        ButtonVariant.Tonal -> {
            ButtonDefaults.buttonColors(
                containerColor = tone.container,
                contentColor = tone.onContainer,
            )
        }

        ButtonVariant.Outlined, ButtonVariant.Ghost, ButtonVariant.Link -> {
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = tone.main,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = scheme.onSurface.copy(alpha = DISABLED_ALPHA),
            )
        }
    }
}

/** Only `Outlined` draws one; the rest would be drawing a transparent line. */
@Composable
@ReadOnlyComposable
fun buttonBorder(
    variant: ButtonVariant,
    color: ButtonColor,
    enabled: Boolean,
): BorderStroke? =
    when (variant) {
        ButtonVariant.Outlined -> {
            BorderStroke(
                width = 1.dp,
                color =
                    if (enabled) {
                        buttonTone(color).main
                    } else {
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = DISABLED_ALPHA)
                    },
            )
        }

        else -> {
            null
        }
    }

/**
 * What Material 3 has no parameter for: the press giving under the finger.
 *
 * Everything M3 *does* express — container, content, border, padding, shape — is absent here on
 * purpose. A `background()` or `shape()` in this block would paint on top of the button M3 already
 * painted, which is the sign the value belonged in [buttonColors] instead.
 */
val buttonStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.97f) } }
        disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
    }

/** Material 3's own disabled opacity, so a disabled wrapper matches a disabled M3 component. */
const val DISABLED_ALPHA = 0.38f

/** The four colours a variant draws from, for one entry of the seven-colour scale. */
@Composable
@ReadOnlyComposable
fun buttonTone(color: ButtonColor): ToneColors {
    val colors = StrangeTheme.colors
    val scheme = colors.scheme
    return when (color) {
        ButtonColor.Primary -> {
            ToneColors(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer)
        }

        ButtonColor.Secondary -> {
            ToneColors(
                scheme.secondary,
                scheme.onSecondary,
                scheme.secondaryContainer,
                scheme.onSecondaryContainer,
            )
        }

        ButtonColor.Success -> {
            colors.tone(Tone.Success)
        }

        ButtonColor.Info -> {
            colors.tone(Tone.Info)
        }

        ButtonColor.Warning -> {
            colors.tone(Tone.Warning)
        }

        ButtonColor.Danger -> {
            colors.tone(Tone.Error)
        }

        ButtonColor.Neutral -> {
            ToneColors(
                scheme.onSurface,
                scheme.surface,
                scheme.surfaceContainerHigh,
                scheme.onSurfaceVariant,
            )
        }
    }
}
