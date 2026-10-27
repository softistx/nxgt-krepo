package com.softistx.material.school.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.feedback.StatusBadge
import com.softistx.material.foundation.theme.Tone
import com.softistx.material.school.data.oneDecimal

/** The tone a mark out of 20 reads as: green from 14, blue from 10, amber from 8, red below. */
fun scoreTone(score: Double): Tone =
    when {
        score >= 14 -> Tone.Success
        score >= 10 -> Tone.Info
        score >= 8 -> Tone.Warning
        else -> Tone.Error
    }

@Composable
fun GradeBadge(
    score: Double,
    modifier: Modifier = Modifier,
) {
    StatusBadge(text = "${score.oneDecimal()}/20", tone = scoreTone(score), modifier = modifier)
}
