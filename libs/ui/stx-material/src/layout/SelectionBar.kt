package com.softistx.material.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.button.IconButton
import com.softistx.material.icon.StxIcons
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/**
 * The bar that appears when a list has a selection. Hidden at count zero.
 *
 * Material 3 has no selection chrome. The close control clears; [actions] is the bulk work —
 * delete, export, assign. The count is the only number that belongs here: not which rows, just
 * how many, so a screen reader hears "3 selected" rather than a list of names in a bar.
 */
@Composable
fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (count <= 0) return
    Surface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = StxTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
        ) {
            IconButton(
                icon = StxIcons.Close,
                description = "Clear selection",
                onClick = onClear,
            )
            Typography(
                text = "$count selected",
                variant = TypographyVariant.TitleSmall,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs),
                content = actions,
            )
        }
    }
}
