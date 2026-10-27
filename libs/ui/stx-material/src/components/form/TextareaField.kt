package com.softistx.material.components.form

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography

/**
 * Several lines of text — the same field, told it may grow.
 *
 * It is a separate composable rather than a `lines` parameter on [TextField] because the two behave
 * differently in ways a parameter cannot express: this one takes the return key instead of moving
 * to the next field, and it counts what has been typed when there is a limit to count against.
 */
@Composable
fun TextareaField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    minLines: Int = 3,
    maxLines: Int = 8,
    maxLength: Int? = null,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // A count is a hint about this field, so it goes where the hint goes rather than beside the
    // label, and it gives way to an error the way any other hint does.
    val counted = maxLength?.let { "${value.length} / $it" }

    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(maxLength?.let { next.take(it) } ?: next) },
        modifier =
            modifier
                .fillMaxWidth()
                .styledBy(style, enabled = enabled, interactionSource = interactionSource),
        enabled = enabled,
        label = label?.let { { Typography(text = it) } },
        placeholder = placeholder?.let { { Typography(text = it) } },
        supportingText =
            (supportingText ?: helper ?: counted)?.let { { Typography(text = it) } },
        isError = isError,
        minLines = minLines,
        maxLines = maxLines,
        interactionSource = interactionSource,
    )
}
