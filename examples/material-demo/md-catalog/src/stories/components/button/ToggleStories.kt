package com.softistx.material.demo.stories.components.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.button.ButtonColor
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.Fab
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.button.IconToggle
import com.softistx.material.components.button.SplitButton
import com.softistx.material.components.button.ToggleButton
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/** The Toggle stories of the Buttons group. */
internal fun StoryGroupBuilder.toggleStories() {
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

    story("Morphing family") { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md)) {
            Typography(
                text = "Press and hold: round corners square off; a checked toggle stays square.",
                variant = TypographyVariant.BodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
            ) {
                val variants = listOf(ButtonVariant.Filled, ButtonVariant.Tonal, ButtonVariant.Outlined, ButtonVariant.Ghost)
                variants.forEach { variant ->
                    IconButton(StxIcons.Add, "Add ${variant.name}", onClick = {}, variant = variant, color = ButtonColor.Primary)
                }
                variants.forEach { variant ->
                    var on by remember { mutableStateOf(variant == ButtonVariant.Filled) }
                    IconToggle(
                        icon = StxIcons.Star,
                        description = "Star ${variant.name}",
                        checked = on,
                        onCheckedChange = { on = it },
                        variant = variant,
                        color = ButtonColor.Primary,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
            ) {
                listOf(ButtonVariant.Filled, ButtonVariant.Tonal, ButtonVariant.Outlined).forEach { variant ->
                    var on by remember { mutableStateOf(variant != ButtonVariant.Tonal) }
                    ToggleButton(text = variant.name, checked = on, onCheckedChange = { on = it }, variant = variant)
                }
                SplitButton(text = "Save", onClick = {}, overflow = listOf(MenuItem("Save as draft", onClick = {})))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                Fab(icon = StxIcons.Add, description = "New", onClick = {})
                Fab(icon = StxIcons.Add, description = "New order", onClick = {}, text = "New order")
            }
        }
    }
}
