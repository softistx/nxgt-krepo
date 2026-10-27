package com.softistx.material.school.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.softistx.material.components.content.Card
import com.softistx.material.components.content.Chip
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.form.Switch
import com.softistx.material.components.form.ThemeToggle
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.supportsDynamicColor
import com.softistx.material.school.session.Preferences
import com.softistx.material.school.session.SchoolSeeds

/** Light, dark or system; the seed the whole palette is made from; wallpaper colours where offered. */
@Composable
fun AppearanceSection(preferences: Preferences) {
    Card {
        SectionHeader(title = "Appearance", supporting = "Every screen repaints from these")
        ThemeToggle(value = preferences.colorMode, onChange = { preferences.colorMode = it })
        Typography(text = "Accent colour", variant = TypographyVariant.LabelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
            SchoolSeeds.forEach { (name, color) ->
                Chip(
                    text = name,
                    selected = preferences.seed == color && !preferences.dynamicColor,
                    onClick = {
                        preferences.seed = color
                        preferences.dynamicColor = false
                    },
                    leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(color)) },
                )
            }
        }
        if (supportsDynamicColor) {
            Switch(
                value = preferences.dynamicColor,
                onValueChange = { preferences.dynamicColor = it },
                label = "Use wallpaper colours",
                description = "Android 12 and later",
            )
        }
    }
}
