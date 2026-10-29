package com.softistx.material.components.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
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
import com.softistx.material.components.button.IconButton
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.LocalStxStrings
import com.softistx.material.foundation.theme.StxTheme

/**
 * Text that becomes a field when asked. Material 3 has no inline editor.
 *
 * The draft is local until it is committed — IME Done or the check — so Escape (or the close
 * control) can put the previous value back. A live-writing field that cannot be cancelled is just
 * a [TextField]. [style] dresses the field while editing and the text at rest.
 */
@Composable
fun InlineEdit(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = LocalStxStrings.current.addTitle,
    enabled: Boolean = true,
    variant: TypographyVariant = TypographyVariant.TitleMedium,
    style: Style = Style,
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
                style = style,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { commit() }),
            )
            IconButton(
                icon = StxIcons.Check,
                description = LocalStxStrings.current.save,
                onClick = { commit() },
                enabled = enabled,
            )
            IconButton(
                icon = StxIcons.Close,
                description = LocalStxStrings.current.cancel,
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
                        .styledBy(style, enabled = enabled, base = dimmedStyle)
                        .clickable(enabled = enabled) { editing = true },
            )
            if (enabled) {
                IconButton(
                    icon = StxIcons.Edit,
                    description = LocalStxStrings.current.edit,
                    onClick = { editing = true },
                )
            }
        }
    }
}
