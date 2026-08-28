package com.strange.material.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.motion.Transitions
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone
import com.strange.material.theme.colors
import com.strange.material.theme.radii
import com.strange.material.theme.spacing

/**
 * A message about what just happened, or about what is about to.
 *
 * ```kotlin
 * Alert("Vos modifications ont été enregistrées.", tone = Tone.Success)
 * ```
 *
 * It fades and rises into place on its own — [visible] drives an `AnimatedVisibility` inside the
 * component, so a caller that flips a boolean gets the animation without asking for it. Passing
 * `visible = false` initially and flipping it is the whole API for "show this when the save
 * succeeds".
 */
@Composable
fun Alert(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    title: String? = null,
    visible: Boolean = true,
    style: Style = Style,
    action: @Composable (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = Transitions.riseIn,
        exit = Transitions.fadeAway,
    ) {
        val styleState = remember { MutableStyleState(MutableInteractionSource()) }
        Row(
            modifier = modifier.fillMaxWidth().styleable(styleState, alertStyle(tone), style),
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
            ) {
                if (title != null) {
                    Typography(text = title, variant = TypographyVariant.TitleSmall)
                }
                Typography(
                    text = text,
                    variant = TypographyVariant.BodyMedium,
                    emphasis = if (title != null) Emphasis.Medium else Emphasis.Full,
                )
            }
            action?.invoke()
        }
    }
}

/**
 * The container tone plus a rule down the leading edge.
 *
 * The rule matters more than it looks: a tinted container alone is easy to miss against a tinted
 * page, and it is the one part of the treatment that survives a colour-blind reader.
 */
private fun alertStyle(tone: Tone): Style =
    Style {
        val role = colors.tone(tone)
        background(role.container)
        contentColor(role.onContainer)
        shape(RoundedCornerShape(radii.md))
        border(1.dp, role.main)
        contentPadding(spacing.md)
    }
