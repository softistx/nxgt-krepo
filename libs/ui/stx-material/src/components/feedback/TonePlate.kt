package com.softistx.material.components.feedback

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
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/** The icon a [Tone] wears on its plate when the caller names none. */
internal fun toneIcon(tone: Tone): ImageVector =
    when (tone) {
        Tone.Success -> StxIcons.Check
        Tone.Info -> StxIcons.Info
        Tone.Warning, Tone.Error -> StxIcons.Warning
    }

/**
 * The tone of a notice, carried by a small `MaterialShapes.Cookie6Sided` plate with an icon.
 *
 * A notice spends one spot of colour here instead of a slab. The plate takes the tone's container
 * pair, except for an error: the notice itself is already `errorContainer`, so the plate takes the
 * solid pair to stay visible on it.
 *
 * Decorative: the icon has no description, because the notice's own text says what the tone means.
 */
@Composable
internal fun TonePlate(
    tone: Tone,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    val role = StxTheme.colors.tone(tone)
    val (plate, ink) = if (tone == Tone.Error) role.main to role.onMain else role.container to role.onContainer
    Box(
        modifier = modifier.size(PlateSize).clip(MaterialShapes.Cookie6Sided.toShape()).background(plate),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon ?: toneIcon(tone), description = null, size = IconSize.Medium, tint = ink)
    }
}

internal val PlateSize = 36.dp
