package com.softistx.material.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import com.softistx.material.button.IconButton
import com.softistx.material.icon.StxIcons
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/**
 * Text that becomes a field when asked. Material 3 has no inline editor.
 *
 * The draft is local until it is committed — IME Done or the check — so Escape (or the close
 * control) can put the previous value back. A live-writing field that cannot be cancelled is just
 * a [TextField].
 */
@Composable
fun InlineEdit(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Add a title",
    enabled: Boolean = true,
    variant: TypographyVariant = TypographyVariant.TitleMedium,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }

    fun commit() {
        onValueChange(draft.trim())
        editing = false
    }

    fun cancel() {
        draft = value
        editing = false
    }

    if (editing) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                cancel()
                                true
                            } else {
                                false
                            }
                        },
                placeholder = placeholder,
                enabled = enabled,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { commit() }),
            )
            IconButton(
                icon = StxIcons.Check,
                description = "Save",
                onClick = { commit() },
                enabled = enabled,
            )
            IconButton(
                icon = StxIcons.Close,
                description = "Cancel",
                onClick = { cancel() },
            )
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
        ) {
            Typography(
                text = value.ifBlank { placeholder },
                variant = variant,
                emphasis = if (value.isBlank()) Emphasis.Subtle else Emphasis.Full,
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(enabled = enabled) { editing = true },
            )
            if (enabled) {
                IconButton(
                    icon = StxIcons.Edit,
                    description = "Edit",
                    onClick = { editing = true },
                )
            }
        }
    }
}
