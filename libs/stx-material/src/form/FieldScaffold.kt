package com.strange.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.theme.StrangeTheme

/**
 * Label above, control, message below — for the controls Material 3 does not decorate.
 *
 * A text field needs none of this: M3's own takes `label`, `supportingText` and `isError` and
 * arranges them itself, and wrapping it in a second label would give the reader two. A checkbox, a
 * radio group, a switch and a slider get nothing of the kind, so their chrome lives here — once,
 * rather than four times.
 */
@Composable
fun FieldScaffold(
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    optional: Boolean = false,
    helper: String? = null,
    error: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
    ) {
        if (label != null) {
            ExtendedLabel(text = label, required = required, optional = optional)
        }
        content()
        if (helper != null || error != null) {
            HelperText(helper = helper, error = error)
        }
    }
}
