package com.softistx.material.components.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.components.button.ConnectedToggleGroup
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * A single choice among a short, closed set, as Material 3's connected button group: toggle
 * buttons that touch, round at the two ends and morph under the press. The chosen one is filled
 * and shows no checkmark.
 *
 * It is not a [com.softistx.material.components.button.ButtonRow] (a layout) and not [Tabs] (a destination).
 * Three to five options that partition one view — "Day / Week / Month" — is the size it is for.
 * Each segment announces `Role.RadioButton` and `selected`; the row is a selectable group.
 * [icons] puts a glyph before the label of the segment at the same index.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icons: List<ImageVector>? = null,
    style: Style = Style,
) {
    ConnectedToggleGroup(
        count = options.size,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
        style = style,
    ) { index ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icons?.getOrNull(index)?.let { Icon(icon = it, description = null) }
            Typography(text = options[index], variant = TypographyVariant.LabelLarge)
        }
    }
}
