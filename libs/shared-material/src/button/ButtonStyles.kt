package com.strange.material.button

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.strange.material.theme.Tone
import com.strange.material.theme.ToneColors
import com.strange.material.theme.colors
import com.strange.material.theme.motion
import com.strange.material.theme.radii
import com.strange.material.theme.scheme
import com.strange.material.theme.spacing

/**
 * The `variant × color` matrix, resolved in exactly one place.
 *
 * Five variants times seven colours is thirty-five combinations, and the only thing keeping that
 * coherent is that one function decides all of them. Adding a colour is an enum entry and a branch
 * in [palette]; adding a variant is a branch here. Neither touches `Button.kt` — the open/closed
 * rule, spelled concretely.
 *
 * Expressing this as a [Style] rather than as a `ButtonColors` is what buys the interaction states:
 * pressed, hovered and disabled are part of the look, declared beside it, and `animate` makes the
 * transitions between them free — no `animateColorAsState`, no remembered floats, nothing for a
 * caller to wire up.
 */
fun buttonStyle(
    variant: ButtonVariant,
    color: ButtonColor,
): Style =
    Style {
        val tone = palette(color)
        val inline = variant == ButtonVariant.Link

        shape(RoundedCornerShape(radii.full))
        contentPaddingHorizontal(if (inline) spacing.none else spacing.md)
        contentPaddingVertical(if (inline) spacing.none else spacing.sm)
        // 40dp is the smallest a button may be and still be a comfortable target; a Link is text
        // in a sentence and must not carry a button's height.
        minHeight(if (inline) 0.dp else 40.dp)

        when (variant) {
            ButtonVariant.Filled -> {
                background(tone.main)
                contentColor(tone.onMain)
            }

            ButtonVariant.Tonal -> {
                background(tone.container)
                contentColor(tone.onContainer)
            }

            ButtonVariant.Outlined -> {
                border(1.dp, tone.main)
                contentColor(tone.main)
            }

            ButtonVariant.Ghost -> {
                contentColor(tone.main)
            }

            ButtonVariant.Link -> {
                contentColor(tone.main)
                textDecoration(TextDecoration.Underline)
            }
        }

        hovered {
            animate {
                if (variant == ButtonVariant.Filled || variant == ButtonVariant.Tonal) {
                    alpha(0.92f)
                } else {
                    background(tone.main.copy(alpha = 0.08f))
                }
            }
        }

        pressed {
            animate(motion.spec(motion.instant)) { scale(0.97f) }
        }

        disabled {
            animate {
                alpha(0.38f)
                if (variant == ButtonVariant.Filled || variant == ButtonVariant.Tonal) {
                    background(scheme.onSurface.copy(alpha = 0.12f))
                    contentColor(scheme.onSurface)
                }
            }
        }
    }

/** What each [ButtonColor] resolves to in the current theme. */
private fun StyleScope.palette(color: ButtonColor): ToneColors =
    when (color) {
        ButtonColor.Primary -> {
            ToneColors(
                scheme.primary,
                scheme.onPrimary,
                scheme.primaryContainer,
                scheme.onPrimaryContainer,
            )
        }

        ButtonColor.Secondary -> {
            ToneColors(
                scheme.secondary,
                scheme.onSecondary,
                scheme.secondaryContainer,
                scheme.onSecondaryContainer,
            )
        }

        ButtonColor.Neutral -> {
            ToneColors(
                scheme.onSurface,
                scheme.surface,
                scheme.surfaceContainerHigh,
                scheme.onSurface,
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
    }
