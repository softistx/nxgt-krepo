package com.softistx.material.components.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.LocalStxStrings
import com.softistx.material.foundation.theme.StxTheme

/**
 * Previous / next, named for a keyset page.
 *
 * `hasMore` is the cursor, not a page number. A numbered pager pretends the store knows how many
 * rows it has; a keyset only knows whether there is another page.
 *
 * [style] goes to both buttons, which already carry `StxStyles.button`.
 */
@Composable
fun Pagination(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    style: Style = Style,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
    ) {
        Button(
            text = LocalStxStrings.current.previous,
            onClick = onPrevious,
            variant = ButtonVariant.Ghost,
            enabled = hasPrevious,
            style = style,
        )
        if (label != null) {
            Typography(text = label)
        }
        Button(
            text = LocalStxStrings.current.next,
            onClick = onNext,
            variant = ButtonVariant.Ghost,
            enabled = hasNext,
            style = style,
        )
    }
}
