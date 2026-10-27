package com.softistx.material.components.data

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.navigation.Search
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/** One command in a [CommandPalette]. [shortcut] is decoration ("⌘K"); the host wires the key. */
@Immutable
data class CommandItem(
    val label: String,
    val onRun: () -> Unit,
    val group: String? = null,
    val shortcut: String? = null,
)

/**
 * A searchable list of actions, in a dialog.
 *
 * Material 3 has no command palette. The host opens it (typically ⌘K / Ctrl+K); this filters
 * [items] as the query changes and runs the selected command. Matching is a case-insensitive
 * contains — fuzzy ranking is the caller's if they outgrow that.
 *
 * [style] dresses the list of commands, not the dialog: M3's `AlertDialog` paints its own container
 * over its modifier, and each command is a `ListTile` with the list-tile style.
 */
@Composable
fun CommandPalette(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<CommandItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    if (!visible) return
    val styleState = rememberUpdatedStyleState(remember { MutableInteractionSource() }) { it.isEnabled = true }
    val needle = query.trim()
    val shown =
        if (needle.isEmpty()) {
            items
        } else {
            items.filter { it.label.contains(needle, ignoreCase = true) }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        modifier = modifier,
        title = { Typography(text = "Command", variant = TypographyVariant.TitleLarge) },
        text = {
            Column {
                Search(query = query, onQueryChange = onQueryChange, placeholder = "Type a command")
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .styleable(styleState, style)
                            .verticalScroll(rememberScrollState())
                            .padding(top = StxTheme.spacing.sm),
                ) {
                    var lastGroup: String? = null
                    shown.forEach { item ->
                        if (item.group != null && item.group != lastGroup) {
                            Typography(
                                text = item.group,
                                variant = TypographyVariant.Overline,
                                emphasis = Emphasis.Subtle,
                                modifier = Modifier.padding(vertical = StxTheme.spacing.xs),
                            )
                            lastGroup = item.group
                        }
                        ListTile(
                            title = item.label,
                            supporting = item.shortcut,
                            onClick = {
                                onDismiss()
                                item.onRun()
                            },
                        )
                    }
                    if (shown.isEmpty()) {
                        Typography(
                            text = "No matching commands",
                            emphasis = Emphasis.Medium,
                            modifier = Modifier.padding(StxTheme.spacing.md),
                        )
                    }
                }
            }
        },
    )
}

/** Case-insensitive contains. Extracted so a spec can ask it without composing a dialog. */
fun filterCommands(
    items: List<CommandItem>,
    query: String,
): List<CommandItem> {
    val needle = query.trim()
    if (needle.isEmpty()) return items
    return items.filter { it.label.contains(needle, ignoreCase = true) }
}
