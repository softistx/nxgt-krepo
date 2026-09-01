package com.softistx.material.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.motion.Transitions
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme
import com.softistx.material.theme.Tone

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
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs),
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
