package com.softistx.material.display

import androidx.compose.foundation.clickable
import androidx.compose.material3.InputChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.icon.Icon
import com.softistx.material.icon.IconSize
import com.softistx.material.icon.StxIcons
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant

/**
 * A file the reader has already chosen. Material 3's `InputChip`.
 *
 * Pairs with [com.softistx.material.form.UploadField]: that one is the well, this one is what
 * landed in it. [sizeLabel] is a string because only the host knows whether it is "240 KB" or
 * "3 pages". [onRemove] is optional — a chip with no way out is a receipt.
 */
@Composable
fun FileChip(
    name: String,
    modifier: Modifier = Modifier,
    sizeLabel: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val label = if (sizeLabel == null) name else "$name · $sizeLabel"
    InputChip(
        selected = false,
        onClick = onClick ?: {},
        label = { Typography(text = label, variant = TypographyVariant.LabelMedium) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = { Icon(icon = StxIcons.Attach, description = null, size = IconSize.Small) },
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
