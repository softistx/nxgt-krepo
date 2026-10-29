package com.softistx.material.components.surface

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.IconButton
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.LocalStxStrings

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
    description: String = LocalStxStrings.current.moreInformation,
    style: Style = Style,
) {
    Tooltip(text = text, modifier = modifier) {
        IconButton(
            icon = StxIcons.Info,
            description = description,
            onClick = {},
            style = style,
        )
    }
}
