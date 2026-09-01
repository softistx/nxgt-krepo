package com.softistx.material.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.InputChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.softistx.material.icon.Icon
import com.softistx.material.icon.IconSize
import com.softistx.material.icon.StxIcons
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/** Adds [raw] if it is not blank and not already present, ignoring case. */
fun addTag(
    tags: List<String>,
    raw: String,
): List<String> {
    val tag = raw.trim()
    if (tag.isEmpty()) return tags
    if (tags.any { it.equals(tag, ignoreCase = true) }) return tags
    return tags + tag
}

/** Drops the first matching name, ignoring case. */
fun removeTag(
    tags: List<String>,
    tag: String,
): List<String> = tags.filterNot { it.equals(tag, ignoreCase = true) }

/**
 * A set of names the reader can add to and take from. Material 3's `InputChip` for each tag.
 *
 * Type and confirm (IME Done, a trailing comma, or Enter) to add. Backspace on an empty draft
 * removes the last tag. The empty list is "none", which is the honest default for a set the
 * reader builds.
 */
@Composable
fun TagField(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Add a tag",
    helper: String? = null,
    enabled: Boolean = true,
) {
    var draft by remember { mutableStateOf("") }

    fun commit() {
        val next = addTag(tags, draft)
        if (next != tags) onTagsChange(next)
        draft = ""
    }

    FieldScaffold(modifier = modifier, label = label, helper = helper) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Typography(text = tag, variant = TypographyVariant.LabelMedium) },
                    enabled = enabled,
                    trailingIcon = {
                        Icon(
                            icon = StxIcons.Close,
                            description = "Remove $tag",
                            size = IconSize.Small,
                            modifier =
                                Modifier.clickable(enabled = enabled) {
                                    onTagsChange(removeTag(tags, tag))
                                },
                        )
                    },
                )
            }
            TextField(
                value = draft,
                onValueChange = { value ->
                    if (value.endsWith(',') || value.endsWith('\n')) {
                        val next = addTag(tags, value.trimEnd(',', '\n'))
                        if (next != tags) onTagsChange(next)
                        draft = ""
                    } else {
                        draft = value
                    }
                },
                modifier =
                    Modifier
                        .widthIn(min = 96.dp)
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Backspace &&
                                draft.isEmpty() &&
                                tags.isNotEmpty() &&
                                enabled
                            ) {
                                onTagsChange(tags.dropLast(1))
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
        }
    }
}
