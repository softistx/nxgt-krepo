package com.softistx.material.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

@Immutable
data class DescriptionItem(
    val term: String,
    val detail: String,
)

/**
 * A term/detail list. Not a table and not a form — the read-only properties of one thing.
 */
@Composable
fun Description(
    items: List<DescriptionItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
            ) {
                Typography(
                    text = item.term,
                    variant = TypographyVariant.LabelLarge,
                    emphasis = Emphasis.Medium,
                    modifier = Modifier.weight(0.4f),
                )
                Typography(
                    text = item.detail,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
    }
}
