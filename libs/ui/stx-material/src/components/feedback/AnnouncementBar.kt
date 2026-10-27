package com.softistx.material.components.feedback

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.components.button.IconButton
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/**
 * A slim, full-width notice. Material 3 has no banner.
 *
 * [Alert] is a paragraph on a page. This is the strip at the top: maintenance, offline, a
 * shipping delay. [onDismiss] missing means the bar cannot be put away — an outage is not a
 * toast.
 *
 * Same surface as [Alert] — tonal, no outline, `largeIncreased` — with the tone's icon on a
 * leading plate ([icon] replaces it, [showIcon] = false drops it).
 */
@Composable
fun AnnouncementBar(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    style: Style = Style,
    onDismiss: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    icon: ImageVector? = null,
    showIcon: Boolean = true,
) {
    require(showIcon || icon == null) { "AnnouncementBar: icon is set but showIcon = false; drop one of them" }
    val styleState = remember { MutableStyleState(MutableInteractionSource()) }
    Row(
        modifier = modifier.fillMaxWidth().styleable(styleState, alertStyle(tone), style),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
    ) {
        if (showIcon) TonePlate(tone, icon)
        Typography(text = text, modifier = Modifier.weight(1f))
        action?.invoke()
        if (onDismiss != null) {
            IconButton(
                icon = StxIcons.Close,
                description = "Dismiss",
                onClick = onDismiss,
            )
        }
    }
}
