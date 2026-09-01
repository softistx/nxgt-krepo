package com.softistx.material.demo.stories

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.button.BusyButton
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonColor
import com.softistx.material.button.ButtonRow
import com.softistx.material.button.ButtonVariant
import com.softistx.material.button.ConfirmButton
import com.softistx.material.button.CopyButton
import com.softistx.material.button.Fab
import com.softistx.material.button.FabAction
import com.softistx.material.button.FabMenu
import com.softistx.material.button.IconBadge
import com.softistx.material.button.IconButton
import com.softistx.material.button.IconToggle
import com.softistx.material.button.MoreMenu
import com.softistx.material.button.OverflowAction
import com.softistx.material.button.OverflowBar
import com.softistx.material.button.ResponsiveButton
import com.softistx.material.button.SplitButton
import com.softistx.material.button.ToggleButton
import com.softistx.material.button.ViewMode
import com.softistx.material.button.ViewToggle
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.demo.storyGroup
import com.softistx.material.icon.IconSize
import com.softistx.material.icon.StxIcons
import com.softistx.material.surface.MenuItem
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

val ButtonStories =
    storyGroup("Buttons") {
        story("Button") { knobs ->
            Button(
                text = knobs.text("Label", "Save changes"),
                onClick = {},
                variant = knobs.enumChoice("Variant", ButtonVariant.Filled),
                color = knobs.enumChoice("Color", ButtonColor.Primary),
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Variant and colour matrix") { knobs ->
            val enabled = knobs.flag("Enabled", true)
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.lg)) {
                ButtonVariant.entries.forEach { variant ->
                    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs)) {
                        Typography(
                            text = variant.name,
                            variant = TypographyVariant.Overline,
                            emphasis = Emphasis.Subtle,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
                        ) {
                            ButtonColor.entries.forEach { color ->
                                Button(
                                    text = color.name,
                                    onClick = {},
                                    variant = variant,
                                    color = color,
                                    enabled = enabled,
                                )
                            }
                        }
                    }
                }
            }
        }

        story("Icon button") { knobs ->
            val variant = knobs.enumChoice("Variant", ButtonVariant.Ghost)
            val color = knobs.enumChoice("Color", ButtonColor.Neutral)
            val size = knobs.enumChoice("Size", IconSize.Medium)
            val enabled = knobs.flag("Enabled", true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                listOf(
                    StxIcons.Add to "Add",
                    StxIcons.Edit to "Edit",
                    StxIcons.Delete to "Delete",
                ).forEach { (icon, description) ->
                    IconButton(
                        icon = icon,
                        description = description,
                        onClick = {},
                        variant = variant,
                        color = color,
                        size = size,
                        enabled = enabled,
                    )
                }
            }
        }

        story("Responsive button") { knobs ->
            val width = knobs.number("Available width", 420f, 120f..640f)
            Box(modifier = Modifier.width(width.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                ResponsiveButton(
                    text = knobs.text("Label", "New order"),
                    icon = StxIcons.Add,
                    onClick = {},
                    collapseBelow = knobs.number("Collapse below", 360f, 120f..640f).dp,
                )
            }
        }

        story("Button row") { knobs ->
            val destructive = knobs.flag("Destructive confirm", false)
            ButtonRow {
                Button(text = "Cancel", onClick = {}, variant = ButtonVariant.Ghost)
                Button(
                    text = if (destructive) "Delete" else "Confirm",
                    onClick = {},
                    color = if (destructive) ButtonColor.Danger else ButtonColor.Primary,
                )
            }
        }

        story("Fab") { knobs ->
            Fab(
                icon = StxIcons.Add,
                description = "New order",
                onClick = {},
                text = knobs.text("Label", "New order").ifBlank { null },
                expanded = knobs.flag("Expanded", true),
                color = knobs.enumChoice("Color", ButtonColor.Primary),
            )
        }

        story("Fab menu") { _ ->
            var open by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.BottomEnd) {
                FabMenu(
                    expanded = open,
                    onExpandedChange = { open = it },
                    actions =
                        listOf(
                            FabAction("New order", StxIcons.Add, onClick = { open = false }),
                            FabAction("Edit", StxIcons.Edit, onClick = { open = false }),
                            FabAction("Delete", StxIcons.Delete, onClick = { open = false }),
                        ),
                )
            }
        }

        story("Split button") { knobs ->
            SplitButton(
                text = knobs.text("Label", "Save"),
                onClick = {},
                overflow =
                    listOf(
                        MenuItem("Save as draft", onClick = {}),
                        MenuItem("Save and publish", onClick = {}),
                    ),
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Toggle button") { knobs ->
            var following by remember { mutableStateOf(true) }
            ToggleButton(
                text = if (following) "Following" else "Follow",
                checked = following,
                onCheckedChange = { following = it },
                variant = knobs.enumChoice("Variant", ButtonVariant.Tonal),
                color = knobs.enumChoice("Color", ButtonColor.Primary),
                icon = StxIcons.Person,
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Icon toggle") { knobs ->
            var saved by remember { mutableStateOf(true) }
            IconToggle(
                icon = StxIcons.Star,
                description = if (saved) "Unsave" else "Save",
                checked = saved,
                onCheckedChange = { saved = it },
                variant = knobs.enumChoice("Variant", ButtonVariant.Ghost),
                color = ButtonColor.Warning,
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Copy button") { knobs ->
            CopyButton(
                text = knobs.text("Value", "ord_9f3a"),
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Confirm button") { knobs ->
            ConfirmButton(
                text = knobs.text("Label", "Delete"),
                onConfirm = {},
                confirmText = "Confirm delete?",
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Busy button") { knobs ->
            BusyButton(
                text = knobs.text("Label", "Save changes"),
                onClick = {},
                busy = knobs.flag("Busy", true),
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("More menu") { _ ->
            MoreMenu(
                items =
                    listOf(
                        MenuItem("Edit", onClick = {}, leading = StxIcons.Edit),
                        MenuItem("Delete", onClick = {}, leading = StxIcons.Delete),
                    ),
            )
        }

        story("Overflow bar") { knobs ->
            val max = knobs.number("Max visible", 2f, 1f..5f, steps = 3).toInt()
            Box(modifier = Modifier.width(280.dp)) {
                OverflowBar(
                    actions =
                        listOf(
                            OverflowAction("Edit", StxIcons.Edit, onClick = {}),
                            OverflowAction("Delete", StxIcons.Delete, onClick = {}),
                            OverflowAction("Search", StxIcons.Search, onClick = {}),
                            OverflowAction("Share", StxIcons.Copy, onClick = {}),
                        ),
                    maxVisible = max,
                )
            }
        }

        story("Icon badge") { knobs ->
            IconBadge(
                icon = StxIcons.Inbox,
                description = "Inbox",
                onClick = {},
                count = knobs.number("Count", 3f, 0f..120f, steps = 23).toInt(),
            )
        }

        story("View toggle") { _ ->
            var mode by remember { mutableStateOf(ViewMode.List) }
            ViewToggle(value = mode, onChange = { mode = it })
        }
    }
