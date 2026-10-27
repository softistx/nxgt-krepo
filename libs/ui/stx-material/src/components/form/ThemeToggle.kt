package com.softistx.material.components.form

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.navigation.SegmentedControl
import com.softistx.material.foundation.theme.ColorMode

/**
 * Light, dark or the platform. Material 3's `SingleChoiceSegmentedButtonRow` via
 * [SegmentedControl].
 *
 * The host still installs the scheme — this only names the choice. [ColorMode.System] is the
 * one that belongs as a default, so a settings screen that starts on Light is already an
 * opinion.
 */
@Composable
fun ThemeToggle(
    value: ColorMode,
    onChange: (ColorMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Style = Style,
) {
    SegmentedControl(
        options = listOf("Light", "Dark", "System"),
        selected = value.ordinal,
        onSelect = { onChange(ColorMode.entries[it]) },
        modifier = modifier,
        enabled = enabled,
        style = style,
    )
}
