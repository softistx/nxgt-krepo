package com.strange.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme
import androidx.compose.material3.TriStateCheckbox as MaterialTriStateCheckbox

/** The three values a parent checkbox can take when its children disagree. */
enum class CheckState {
    Off,
    On,
    Indeterminate,
}

fun CheckState.toToggleableState(): ToggleableState =
    when (this) {
        CheckState.Off -> ToggleableState.Off
        CheckState.On -> ToggleableState.On
        CheckState.Indeterminate -> ToggleableState.Indeterminate
    }

/**
 * Cycles [CheckState] Off → On → Indeterminate → Off. The parent of a group uses this; a
 * two-state box is [Checkbox].
 */
fun cycleCheckState(state: CheckState): CheckState =
    when (state) {
        CheckState.Off -> CheckState.On
        CheckState.On -> CheckState.Indeterminate
        CheckState.Indeterminate -> CheckState.Off
    }

/**
 * A checkbox that can be neither on nor off. Material 3's `TriStateCheckbox`.
 *
 * The whole row is the target, label included — the same rule as [Checkbox]. [onClick] fires on
 * each tap; [cycleCheckState] is the usual next value, kept out of the composable so a parent
 * that maps "some children" onto Indeterminate can do its own arithmetic.
 */
@Composable
fun TriStateCheckbox(
    state: CheckState,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    enabled: Boolean = true,
) {
    val toggleable = state.toToggleableState()
    Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs)) {
        Row(
            modifier =
                modifier.triStateToggleable(
                    state = toggleable,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onClick = onClick,
                ),
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialTriStateCheckbox(
                state = toggleable,
                onClick = null,
                enabled = enabled,
            )
            Typography(text = label)
        }
        if (helper != null) {
            HelperText(helper = helper)
        }
    }
}
