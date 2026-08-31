package com.strange.material.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.button.CopyButton

/**
 * A value meant to be copied, not edited. A read-only [TextField] with a [CopyButton] in the
 * trailing slot.
 *
 * Invite links, API keys, order ids — the field is there so the value can be selected, the button
 * so it does not have to be.
 */
@Composable
fun CopyField(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    helper: String? = null,
) {
    TextField(
        value = value,
        onValueChange = {},
        modifier = modifier,
        label = label,
        helper = helper,
        readOnly = true,
        trailing = { CopyButton(text = value) },
    )
}
