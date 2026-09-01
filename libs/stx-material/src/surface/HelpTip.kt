package com.softistx.material.surface

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.button.IconButton
import com.softistx.material.icon.StxIcons

/**
 * A question mark next to a label. [Tooltip] wrapping an [IconButton].
 *
 * The host writes the sentence; this is the affordance. An info icon with no tooltip is a
 * control that does nothing — wrapping them together is what stops that.
 */
@Composable
fun HelpTip(
    text: String,
    modifier: Modifier = Modifier,
    description: String = "More information",
) {
    Tooltip(text = text, modifier = modifier) {
        IconButton(
            icon = StxIcons.Info,
            description = description,
            onClick = {},
        )
    }
}
