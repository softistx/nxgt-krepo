package com.strange.material.demo.stories

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.button.Button
import com.strange.material.button.ButtonColor
import com.strange.material.button.ButtonRow
import com.strange.material.button.ButtonVariant
import com.strange.material.button.IconButton
import com.strange.material.button.ResponsiveButton
import com.strange.material.demo.knobs.enumChoice
import com.strange.material.demo.storyGroup
import com.strange.material.icon.IconSize
import com.strange.material.icon.StrangeIcons
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
            Box(modifier = Modifier.width(width.dp).border(1.dp, StrangeTheme.colors.scheme.outlineVariant)) {
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
    }
