package com.softistx.material.components.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.material3.InputChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.media.Avatar
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * A person mentioned in a field. Material 3's `InputChip` with an [Avatar].
 *
 * [FileChip] is what landed in an upload; this is who was @-named. [onRemove] missing means the
 * mention is a receipt, not a token the reader can take back.
 */
@Composable
fun MentionChip(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: Any? = null,
    enabled: Boolean = true,
    onRemove: (() -> Unit)? = null,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    InputChip(
        selected = false,
        onClick = onClick,
        label = { Typography(text = name, variant = TypographyVariant.LabelMedium) },
        modifier = modifier.styledBy(style, enabled, chipStyle, interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        leadingIcon = {
            Avatar(name = name, image = image, size = 20.dp, description = null)
        },
        trailingIcon =
            onRemove?.let { remove ->
                {
                    Icon(
                        icon = StxIcons.Close,
                        description = "Remove $name",
                        size = IconSize.Small,
                        modifier = Modifier.clickable(enabled = enabled, onClick = remove),
                    )
                }
            },
    )
}
