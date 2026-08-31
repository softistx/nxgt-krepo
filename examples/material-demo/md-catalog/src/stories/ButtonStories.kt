package com.strange.material.demo.stories

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
import com.strange.material.button.BusyButton
import com.strange.material.button.Button
import com.strange.material.button.ButtonColor
import com.strange.material.button.ButtonRow
import com.strange.material.button.ButtonVariant
import com.strange.material.button.ConfirmButton
import com.strange.material.button.CopyButton
import com.strange.material.button.Fab
import com.strange.material.button.FabAction
import com.strange.material.button.FabMenu
import com.strange.material.button.IconBadge
import com.strange.material.button.IconButton
import com.strange.material.button.IconToggle
import com.strange.material.button.MoreMenu
import com.strange.material.button.OverflowAction
import com.strange.material.button.OverflowBar
import com.strange.material.button.ResponsiveButton
import com.strange.material.button.SplitButton
import com.strange.material.button.ToggleButton
import com.strange.material.button.ViewMode
import com.strange.material.button.ViewToggle
import com.strange.material.demo.knobs.enumChoice
import com.strange.material.demo.storyGroup
import com.strange.material.icon.IconSize
import com.strange.material.icon.StrangeIcons
import com.strange.material.surface.MenuItem
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

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
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.lg)) {
                ButtonVariant.entries.forEach { variant ->
                    Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs)) {
                        Typography(
                            text = variant.name,
                            variant = TypographyVariant.Overline,
                            emphasis = Emphasis.Subtle,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                listOf(
                    StrangeIcons.Add to "Add",
                    StrangeIcons.Edit to "Edit",
                    StrangeIcons.Delete to "Delete",
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
                    icon = StrangeIcons.Add,
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
                icon = StrangeIcons.Add,
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
                            FabAction("New order", StrangeIcons.Add, onClick = { open = false }),
                            FabAction("Edit", StrangeIcons.Edit, onClick = { open = false }),
                            FabAction("Delete", StrangeIcons.Delete, onClick = { open = false }),
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
                icon = StrangeIcons.Person,
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Icon toggle") { knobs ->
            var saved by remember { mutableStateOf(true) }
            IconToggle(
                icon = StrangeIcons.Star,
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
                        MenuItem("Edit", onClick = {}, leading = StrangeIcons.Edit),
                        MenuItem("Delete", onClick = {}, leading = StrangeIcons.Delete),
                    ),
            )
        }

        story("Overflow bar") { knobs ->
            val max = knobs.number("Max visible", 2f, 1f..5f, steps = 3).toInt()
            Box(modifier = Modifier.width(280.dp)) {
                OverflowBar(
                    actions =
                        listOf(
                            OverflowAction("Edit", StrangeIcons.Edit, onClick = {}),
                            OverflowAction("Delete", StrangeIcons.Delete, onClick = {}),
                            OverflowAction("Search", StrangeIcons.Search, onClick = {}),
                            OverflowAction("Share", StrangeIcons.Copy, onClick = {}),
                        ),
                    maxVisible = max,
                )
            }
        }

        story("Icon badge") { knobs ->
            IconBadge(
                icon = StrangeIcons.Inbox,
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
