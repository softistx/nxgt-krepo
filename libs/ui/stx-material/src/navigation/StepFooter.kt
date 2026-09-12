package com.softistx.material.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.button.BusyButton
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonRow
import com.softistx.material.button.ButtonVariant

/**
 * Back and Continue under a [Stepper].
 *
 * [onBack] missing hides Back — the first step has nowhere to go. [busy] turns Continue into a
 * [BusyButton], so a "Create account" that is in flight cannot be tapped twice. The stepper
 * itself never ships its own footer: a wizard that needs a different pair of verbs still uses
 * the trail.
 */
@Composable
fun StepFooter(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    nextLabel: String = "Continue",
    backLabel: String = "Back",
    nextEnabled: Boolean = true,
    busy: Boolean = false,
) {
    ButtonRow(modifier = modifier) {
        if (onBack != null) {
            Button(text = backLabel, onClick = onBack, variant = ButtonVariant.Ghost, enabled = !busy)
        }
        BusyButton(
            text = nextLabel,
            onClick = onNext,
            busy = busy,
            enabled = nextEnabled,
        )
    }
}
