package com.softistx.material.display

import androidx.compose.foundation.clickable
import androidx.compose.material3.InputChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.icon.Icon
import com.softistx.material.icon.IconSize
import com.softistx.material.icon.StrangeIcons
import com.softistx.material.media.Avatar
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant

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
) {
    InputChip(
        selected = false,
        onClick = onClick,
        label = { Typography(text = name, variant = TypographyVariant.LabelMedium) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = {
            Avatar(name = name, image = image, size = 20.dp, description = null)
        },
        trailingIcon =
            onRemove?.let { remove ->
                {
                    Icon(
                        icon = StrangeIcons.Close,
                        description = "Remove $name",
                        size = IconSize.Small,
                        modifier = Modifier.clickable(enabled = enabled, onClick = remove),
                    )
                }
            },
    )
}
