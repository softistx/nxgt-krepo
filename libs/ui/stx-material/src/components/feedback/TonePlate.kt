package com.softistx.material.components.feedback

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.ShapedIconPlate
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/** What a notice is dressed with by default, read by `Alert`, `AnnouncementBar` and `PromoBanner`. */
object AlertDefaults {
    /** The icon a [tone] wears on its plate: check, info or warning. Pass `icon = null` to drop the plate. */
    fun icon(tone: Tone): ImageVector =
        when (tone) {
            Tone.Success -> StxIcons.Check
            Tone.Info -> StxIcons.Info
            Tone.Warning, Tone.Error -> StxIcons.Warning
        }
}

/**
 * The tone of a notice, carried by a small `MaterialShapes.Cookie6Sided` plate with an icon.
 *
 * A notice spends one spot of colour here instead of a slab, and the plate is the tone's solid
 * pair (`main` with `onMain`) for every tone, so it holds its contrast on the notice's surface.
 */
@Composable
internal fun TonePlate(
    tone: Tone,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val role = StxTheme.colors.tone(tone)
    ShapedIconPlate(icon, MaterialShapes.Cookie6Sided.toShape(), role.main, role.onMain, 36.dp, IconSize.Medium, modifier)
}
