package com.softistx.material.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * The title of a block of content, with an optional action on the trailing edge.
 *
 * "Recent orders" plus "See all" — a list without this reads as a pile. Material 3 has no
 * subheader of its own; [com.softistx.material.components.data.EntityHeader] is the top of a *page*.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Typography(text = title, variant = TypographyVariant.TitleSmall)
            if (supporting != null) {
                Typography(text = supporting, emphasis = Emphasis.Medium)
            }
        }
        action?.invoke()
    }
}
