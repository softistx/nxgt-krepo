package com.strange.material.display

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.hovered
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.motion
import com.strange.material.theme.radii
import com.strange.material.theme.scheme
import com.strange.material.theme.spacing

/**
 * A small, self-contained token: a tag, a filter, a selected value.
 *
 * ```kotlin
 * Chip("Livraison rapide")
 * Chip("Payées", selected = paid, onClick = { paid = !paid })
 * ```
 *
 * [selected] is pushed into the style state rather than branched on in the layout, so the
 * transition between the two looks is animated by the Styles API and the component holds no
 * animation code at all.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: Style = Style,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState =
        rememberUpdatedStyleState(interactionSource) {
            it.isEnabled = enabled
            it.isSelected = selected
        }

    Row(
        modifier =
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).styleable(styleState, ChipStyle, style),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Typography(text = text, variant = TypographyVariant.LabelMedium)
        trailing?.invoke()
    }
}

/** One style covering both states, so the unselected-to-selected move animates for free. */
private val ChipStyle: Style =
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
