package com.softistx.material.school.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.feedback.StatusBadge
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.data.oneDecimal

/** A mark below this is the only one that asks the teacher to act. */
private const val PASS_MARK = 10.0

/** The tone a mark out of 20 reads as, or null from 10 up: amber from 8, red below. */
fun scoreTone(score: Double): Tone? =
    when {
        score >= PASS_MARK -> null
        score >= 8 -> Tone.Warning
        else -> Tone.Error
    }

/**
 * A mark out of 20. Colour is the accent budget: only a mark below 10 gets a tone, so a list of
 * marks reads as plain figures with the few that need attention standing out.
 */
@Composable
fun GradeBadge(
    score: Double,
    modifier: Modifier = Modifier,
) {
    val text = "${score.oneDecimal()}/20"
    val tone = scoreTone(score)
    if (tone == null) {
        Typography(text = text, variant = TypographyVariant.LabelMedium, modifier = modifier)
    } else {
        StatusBadge(text = text, tone = tone, modifier = modifier)
    }
}
