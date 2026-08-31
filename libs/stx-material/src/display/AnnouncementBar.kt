package com.strange.material.display

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
import com.strange.material.button.IconButton
import com.strange.material.icon.StrangeIcons
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/**
 * A slim, full-width notice. Material 3 has no banner.
 *
 * [Alert] is a paragraph on a page. This is the strip at the top: maintenance, offline, a
 * shipping delay. [onDismiss] missing means the bar cannot be put away — an outage is not a
 * toast.
 */
@Composable
fun AnnouncementBar(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    style: Style = Style,
    onDismiss: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val styleState = remember { MutableStyleState(MutableInteractionSource()) }
    Row(
        modifier = modifier.fillMaxWidth().styleable(styleState, alertStyle(tone), style),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        Typography(text = text, modifier = Modifier.weight(1f))
        action?.invoke()
        if (onDismiss != null) {
            IconButton(
                icon = StrangeIcons.Close,
                description = "Dismiss",
                onClick = onDismiss,
            )
        }
    }
}
