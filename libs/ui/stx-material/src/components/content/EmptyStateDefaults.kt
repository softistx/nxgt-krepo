package com.softistx.material.components.content

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.ShapedIconPlate
import com.softistx.material.foundation.theme.StxTheme

/** What an [EmptyState] can be dressed with. */
object EmptyStateDefaults {
    /**
     * A 96 dp `MaterialShapes.SoftBurst` plate on `secondaryContainer` holding [icon]: the picture
     * of an empty pane, for the `illustration` slot.
     *
     * ```kotlin
     * EmptyState("No invoices", illustration = { EmptyStateDefaults.IconPlate(StxIcons.Inbox) })
     * ```
     */
    @Composable
    fun IconPlate(
        icon: ImageVector,
        modifier: Modifier = Modifier,
    ) {
        val scheme = StxTheme.colors.scheme
        ShapedIconPlate(
            icon,
            MaterialShapes.SoftBurst.toShape(),
            scheme.secondaryContainer,
            scheme.onSecondaryContainer,
            96.dp,
            IconSize.XLarge,
            modifier,
        )
    }
}
