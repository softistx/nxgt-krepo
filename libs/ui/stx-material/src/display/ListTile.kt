package com.softistx.material.display

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant

/**
 * One row of a list. Material 3's `ListItem`, which already owns the two-line height, the leading
 * and trailing alignment, the content padding and the text colours.
 *
 * A tile is interactive **iff** [onClick] is not null, and only then does it take a ripple and the
 * press scale.
 */
@Composable
fun ListTile(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: Style = Style,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    ListItem(
        headlineContent = { Typography(text = title, variant = TypographyVariant.BodyLarge) },
        modifier =
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).styleable(styleState, listTileStyle, style),
        supportingContent =
            supporting?.let {
                {
                    Typography(
                        text = it,
                        variant = TypographyVariant.BodySmall,
                        emphasis = Emphasis.Medium,
                    )
                }
            },
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(),
    )
}
