package com.strange.material.display

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.Tone
import com.strange.material.theme.colors
import com.strange.material.theme.radii
import com.strange.material.theme.spacing

/**
 * A short word carrying a state — *Payée*, *En attente*, *Échouée*.
 *
 * It takes a [Tone] rather than a colour, which is the point: a reviewer can ask whether a status
 * is really a warning, and cannot be answered with "it's the amber one". The four tones are the
 * same four everything else in the library speaks.
 */
@Composable
fun StatusBadge(
    text: String,
    tone: Tone = Tone.Info,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    // A badge never reacts to a pointer, so its state is created once and never updated —
    // MutableStyleState still wants an interaction source, and an unused one is the honest
    // way to say "this component has no interaction states".
    val styleState = remember { MutableStyleState(MutableInteractionSource()) }
    Box(modifier = modifier.styleable(styleState, badgeStyle(tone), style)) {
        Typography(text = text, variant = TypographyVariant.LabelSmall)
    }
}

/** A badge is its container tone: readable, quiet, and never competing with the row it sits in. */
private fun badgeStyle(tone: Tone): Style =
    Style {
        val role = colors.tone(tone)
        background(role.container)
        contentColor(role.onContainer)
        shape(RoundedCornerShape(radii.full))
        contentPaddingHorizontal(spacing.sm)
        contentPaddingVertical(spacing.xxs)
    }
