package com.softistx.material.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme
import com.softistx.material.theme.Tone

/** How strong a password looks, from empty through four filled segments. */
enum class PasswordGrade {
    Empty,
    Weak,
    Fair,
    Good,
    Strong,
}

/**
 * A cheap, local grade: length, mixed case, a digit, a symbol. Not a breach check — the host
 * still has to ask a service if it cares about "password123".
 */
fun passwordGrade(value: String): PasswordGrade {
    if (value.isEmpty()) return PasswordGrade.Empty
    var score = 0
    if (value.length >= 8) score++
    if (value.length >= 12) score++
    if (value.any { it.isLowerCase() } && value.any { it.isUpperCase() }) score++
    if (value.any { it.isDigit() }) score++
    if (value.any { !it.isLetterOrDigit() }) score++
    return when (score) {
        0, 1 -> PasswordGrade.Weak
        2 -> PasswordGrade.Fair
        3 -> PasswordGrade.Good
        else -> PasswordGrade.Strong
    }
}

/**
 * Four segments under a secret field. Material 3 has no password meter.
 *
 * Filled segments use a [Tone] that steps from danger to success. Empty is quiet, not red — a
 * field the reader has not typed in yet is not already a complaint.
 */
@Composable
fun PasswordMeter(
    value: String,
    modifier: Modifier = Modifier,
) {
    val grade = passwordGrade(value)
    val filled = grade.filled
    val tone = grade.tone
    val color =
        if (tone == null) {
            StrangeTheme.colors.scheme.outlineVariant
        } else {
            StrangeTheme.colors.tone(tone).main
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
        ) {
            repeat(4) { index ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < filled) color else StrangeTheme.colors.scheme.outlineVariant,
                            ),
                )
            }
        }
        if (grade != PasswordGrade.Empty) {
            Typography(
                text = grade.label,
                variant = TypographyVariant.LabelSmall,
                emphasis = Emphasis.Medium,
            )
        }
    }
}

private val PasswordGrade.label: String
    get() =
        when (this) {
            PasswordGrade.Empty -> ""
            PasswordGrade.Weak -> "Weak"
            PasswordGrade.Fair -> "Fair"
            PasswordGrade.Good -> "Good"
            PasswordGrade.Strong -> "Strong"
        }

private val PasswordGrade.filled: Int
    get() =
        when (this) {
            PasswordGrade.Empty -> 0
            PasswordGrade.Weak -> 1
            PasswordGrade.Fair -> 2
            PasswordGrade.Good -> 3
            PasswordGrade.Strong -> 4
        }

private val PasswordGrade.tone: Tone?
    get() =
        when (this) {
            PasswordGrade.Empty -> null
            PasswordGrade.Weak -> Tone.Error
            PasswordGrade.Fair -> Tone.Warning
            PasswordGrade.Good, PasswordGrade.Strong -> Tone.Success
        }
