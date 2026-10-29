package com.softistx.material.components.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.material3.InputChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.style.pressBase
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.LocalStxStrings

/**
 * A file the reader has already chosen. Material 3's `InputChip`.
 *
 * Pairs with [com.softistx.material.components.form.UploadField]: that one is the well, this one is what
 * landed in it. [sizeLabel] is a string because only the host knows whether it is "240 KB" or
 * "3 pages". [onRemove] is optional — a chip with no way out is a receipt.
 * Without [onClick] the chip is a receipt too and takes no press scale.
 */
@Composable
fun FileChip(
    name: String,
    modifier: Modifier = Modifier,
    sizeLabel: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val label = if (sizeLabel == null) name else "$name · $sizeLabel"
    InputChip(
        selected = false,
        onClick = onClick ?: {},
        label = { Typography(text = label, variant = TypographyVariant.LabelMedium) },
        modifier = modifier.styledBy(style, enabled, pressBase(chipStyle, onClick), interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        leadingIcon = { Icon(icon = StxIcons.Attach, description = null, size = IconSize.Small) },
        trailingIcon =
            onRemove?.let { remove ->
                {
                    Icon(
                        icon = StxIcons.Close,
                        description = LocalStxStrings.current.remove(name),
                        size = IconSize.Small,
                        modifier = Modifier.clickable(enabled = enabled, onClick = remove),
                    )
                }
            },
    )
}
