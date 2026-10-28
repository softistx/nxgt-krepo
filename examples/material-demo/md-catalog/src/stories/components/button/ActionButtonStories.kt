package com.softistx.material.demo.stories.components.button

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonColor
import com.softistx.material.components.button.ButtonRow
import com.softistx.material.components.button.ButtonSize
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.button.ResponsiveButton
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/** The ActionButton stories of the Buttons group. */
internal fun StoryGroupBuilder.actionButtonStories() {
    story("Button") { knobs ->
        Button(
            text = knobs.text("Label", "Save changes"),
            onClick = {},
            variant = knobs.enumChoice("Variant", ButtonVariant.Filled),
            color = knobs.enumChoice("Color", ButtonColor.Primary),
            enabled = knobs.flag("Enabled", true),
            size = knobs.enumChoice("Size", ButtonSize.Small),
        )
    }

    story("Size and variant grid") { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
            ButtonSize.entries.forEach { size ->
                Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs)) {
                    Typography(
                        text = size.name,
                        variant = TypographyVariant.Overline,
                        emphasis = Emphasis.Subtle,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
                    ) {
                        ButtonVariant.entries.forEach { variant ->
                            Button(text = variant.name, onClick = {}, variant = variant, size = size)
                        }
                    }
                }
            }
        }
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
}
