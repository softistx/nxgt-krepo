package com.strange.material.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.strange.material.display.Chip
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.supportsDynamicColor

/**
 * The header: the two dials that prove the theme is live. Changing either repaints every story
 * without a single component being told about it — which is the whole claim `StrangeTheme` makes.
 */
@Composable
fun CatalogTopBar(
    state: CatalogState,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(StrangeTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        Typography(text = "stx-material", variant = TypographyVariant.TitleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogSeeds.forEach { (name, color) ->
                Chip(
                    text = name,
                    selected = state.seed == color,
                    onClick = { state.seed = color },
                    leading = {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                    },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogMotion.entries.forEach { option ->
                Chip(
                    text = option.label,
                    selected = state.motion == option,
                    onClick = { state.motion = option },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Typography(
                text = "Dark",
                variant = TypographyVariant.LabelMedium,
                emphasis = Emphasis.Medium,
            )
            Switch(checked = state.isDark, onCheckedChange = { state.isDark = it })
        }
        // Shown only where it does something: a switch that cannot change anything is worse than
        // no switch, and `supportsDynamicColor` is exactly the question a settings screen asks.
        if (supportsDynamicColor) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Typography(
                    text = "Wallpaper colours",
                    variant = TypographyVariant.LabelMedium,
                    emphasis = Emphasis.Medium,
                )
                Switch(
                    checked = state.dynamicColor,
                    onCheckedChange = { state.dynamicColor = it },
                )
            }
        }
    }
}
