package com.softistx.material.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.display.ListTile
import com.softistx.material.navigation.Search
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

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
 */
@Composable
fun CommandPalette(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<CommandItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
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
                            .verticalScroll(rememberScrollState())
                            .padding(top = StrangeTheme.spacing.sm),
                ) {
                    var lastGroup: String? = null
                    shown.forEach { item ->
                        if (item.group != null && item.group != lastGroup) {
                            Typography(
                                text = item.group,
                                variant = TypographyVariant.Overline,
                                emphasis = Emphasis.Subtle,
                                modifier = Modifier.padding(vertical = StrangeTheme.spacing.xs),
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
                            modifier = Modifier.padding(StrangeTheme.spacing.md),
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
