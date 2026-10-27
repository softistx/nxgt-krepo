package com.softistx.material.components.feedback

import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.foundation.icon.StxIcons
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
