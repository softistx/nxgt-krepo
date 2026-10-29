package com.softistx.material.components.button

import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.LocalStxStrings

@Immutable
data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * A FAB that fans out related actions. Material 3's `FloatingActionButtonMenu`.
 *
 * [expanded] is the caller's. The main button swaps Add for Close while the menu is open, which is
 * the pattern M3's own samples use so the way out is the same control as the way in.
 */
@Composable
fun FabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
    description: String? = LocalStxStrings.current.openActions,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            Fab(
                icon = if (expanded) StxIcons.Close else StxIcons.Add,
                description = if (expanded) LocalStxStrings.current.closeActions else description,
                onClick = { onExpandedChange(!expanded) },
            )
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    action.onClick()
                    onExpandedChange(false)
                },
                text = { Typography(text = action.label, variant = TypographyVariant.LabelLarge) },
                icon = { Icon(icon = action.icon, description = null) },
            )
        }
    }
}
