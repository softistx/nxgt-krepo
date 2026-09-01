package com.softistx.material.button

import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.icon.Icon
import com.softistx.material.icon.StrangeIcons
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant

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
    description: String? = "Open actions",
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            Fab(
                icon = if (expanded) StrangeIcons.Close else StrangeIcons.Add,
                description = if (expanded) "Close actions" else description,
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
