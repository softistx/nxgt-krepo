package com.softistx.material.components.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.theme.StxTheme

/** The state of an empty pane, as a `MaterialShapes.SoftBurst` plate on `secondaryContainer`. */
@Composable
internal fun IconPlate(icon: ImageVector) {
    val scheme = StxTheme.colors.scheme
    Box(
        modifier = Modifier.size(96.dp).clip(MaterialShapes.SoftBurst.toShape()).background(scheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description = null, size = IconSize.XLarge, tint = scheme.onSecondaryContainer)
    }
}
