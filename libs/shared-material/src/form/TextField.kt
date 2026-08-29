package com.strange.material.form

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.strange.material.button.IconButton
import com.strange.material.icon.StrangeIcons
import com.strange.material.text.Typography

/**
 * A single line of text.
 *
 * It holds no state and knows nothing about forms: the value, the complaint and whether there is
 * one are all passed in, which is what lets the same control sit on a screen with a
 * `FormState` and on one with a plain `var text by remember`.
 *
 * It is Material 3's `OutlinedTextField`, which already carries the floating label, the supporting
 * text, the error colours and the container. Only two things are added: the reveal button [secret]
 * needs, and the rule that a complaint takes the place of the hint — [supportingText] wins over
 * [helper], because showing both puts the reader's mistake and a piece of advice in the same slot.
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    style: Style = Style,
) {
    var revealed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.styleable(styleState, fieldStyle, style),
        enabled = enabled,
        readOnly = readOnly,
        label = label?.let { { Typography(text = it) } },
        placeholder = placeholder?.let { { Typography(text = it) } },
        leadingIcon = leading,
        trailingIcon =
            when {
                secret -> {
                    {
                        IconButton(
                            icon = if (revealed) StrangeIcons.EyeOff else StrangeIcons.Eye,
                            description = if (revealed) "Hide the value" else "Show the value",
                            onClick = { revealed = !revealed },
                        )
                    }
                }

                else -> {
                    trailing
                }
            },
        supportingText = (supportingText ?: helper)?.let { { Typography(text = it) } },
        isError = isError,
        visualTransformation =
            if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        singleLine = true,
        interactionSource = interactionSource,
    )
}
