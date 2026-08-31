package com.strange.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

/**
 * A number chosen by dragging, with the number visible.
 *
 * Material 3's `Slider` shows its value only while the thumb is held, which is fine for a volume
 * control and not for a form field — the reader needs to see what they are about to submit. So the
 * value is printed beside the label, formatted by the caller, since only the caller knows whether
 * it is "40" or "40 %" or "£40".
 */
@Composable
fun SliderField(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    helper: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    format: (Float) -> String = { it.toInt().toString() },
) {
    FieldScaffold(modifier = modifier, helper = helper, error = supportingText) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtendedLabel(text = label)
                Typography(text = format(value), variant = TypographyVariant.Metric)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = range,
            steps = steps,
        )
    }
}

/**
 * Two numbers chosen by dragging. Material 3's `RangeSlider`, with both ends printed.
 *
 * The same reason as the single-thumb [SliderField]: M3 shows the values only while a thumb is
 * held. A price filter that hides €20–€80 until the reader lets go is not a filter they can read.
 */
@Composable
fun SliderField(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    helper: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    format: (Float) -> String = { it.toInt().toString() },
) {
    FieldScaffold(modifier = modifier, helper = helper, error = supportingText) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtendedLabel(text = label)
                Typography(
                    text = "${format(value.start)} – ${format(value.endInclusive)}",
                    variant = TypographyVariant.Metric,
                )
            }
        }
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = range,
            steps = steps,
        )
    }
}
