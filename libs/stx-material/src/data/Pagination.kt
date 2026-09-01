package com.softistx.material.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonVariant
import com.softistx.material.text.Typography
import com.softistx.material.theme.StrangeTheme

/**
 * Previous / next, named for a keyset page.
 *
 * `hasMore` is the cursor, not a page number. A numbered pager pretends the store knows how many
 * rows it has; a keyset only knows whether there is another page.
 */
@Composable
fun Pagination(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        Button(
            text = "Previous",
            onClick = onPrevious,
            variant = ButtonVariant.Ghost,
            enabled = hasPrevious,
        )
        if (label != null) {
            Typography(text = label)
        }
        Button(
            text = "Next",
            onClick = onNext,
            variant = ButtonVariant.Ghost,
            enabled = hasNext,
        )
    }
}
