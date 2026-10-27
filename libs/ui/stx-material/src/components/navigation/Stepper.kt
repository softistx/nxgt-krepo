package com.softistx.material.components.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * One step in a [Stepper]. The stepper owns whether it is done, current or upcoming from
 * [Stepper]'s `current` index — a caller never holds those flags.
 */
@Immutable
data class Step(
    val label: String,
    val supporting: String? = null,
)

enum class StepStatus {
    Done,
    Current,
    Upcoming,
}

/** Where a step sits relative to [current]. Previous are done, the rest upcoming. */
fun stepStatus(
    index: Int,
    current: Int,
): StepStatus =
    when {
        index < current -> StepStatus.Done
        index == current -> StepStatus.Current
        else -> StepStatus.Upcoming
    }

/**
 * A sequence of steps. Material 3 has no stepper.
 *
 * Completed steps are the ones that can be pressed, so a reader can go back; the current and the
 * upcoming ones are not, so the stepper never skips ahead on a tap. Below [collapseBelow] the
 * trail stacks vertically, which is how it stays readable in a narrow pane.
 */
@Composable
fun Stepper(
    steps: List<Step>,
    current: Int,
    modifier: Modifier = Modifier,
    onStep: ((Int) -> Unit)? = null,
    collapseBelow: Dp = 520.dp,
    style: Style = Style,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val vertical = maxWidth < collapseBelow
        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                steps.forEachIndexed { index, step ->
                    StepRow(
                        index = index,
                        step = step,
                        status = stepStatus(index, current),
                        onStep = onStep,
                        style = style,
                        last = index == steps.lastIndex,
                        vertical = true,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                steps.forEachIndexed { index, step ->
                    StepRow(
                        index = index,
                        step = step,
                        status = stepStatus(index, current),
                        onStep = onStep,
                        style = style,
                        last = index == steps.lastIndex,
                        vertical = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: Step,
    status: StepStatus,
    onStep: ((Int) -> Unit)?,
    style: Style,
    last: Boolean,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val clickable = status == StepStatus.Done && onStep != null
    val interactionSource = remember { MutableInteractionSource() }
    val body =
        Modifier
            .styledBy(style, enabled = clickable, base = stepperStyle, interactionSource = interactionSource)
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        role = Role.Button,
                        onClick = { onStep.invoke(index) },
                    )
                } else {
                    Modifier
                },
            )
    if (vertical) {
        Row(
            modifier = modifier.then(body),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
        ) {
            StepMarker(status = status, index = index, connector = !last, vertical = true)
            Labels(step = step, status = status)
        }
    } else {
        Column(
            modifier = modifier.then(body),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepMarker(status = status, index = index, connector = !last, vertical = false)
            }
            Labels(step = step, status = status)
        }
    }
}

@Composable
private fun Labels(
    step: Step,
    status: StepStatus,
) {
    Column {
        Typography(
            text = step.label,
            variant = TypographyVariant.LabelLarge,
            emphasis = if (status == StepStatus.Upcoming) Emphasis.Medium else Emphasis.Full,
        )
        if (step.supporting != null) {
            Typography(
                text = step.supporting,
                variant = TypographyVariant.BodySmall,
                emphasis = Emphasis.Subtle,
            )
        }
    }
}
