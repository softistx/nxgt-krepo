package com.softistx.material.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * The title of a block of content, with an optional action on the trailing edge.
 *
 * "Recent orders" plus "See all" — a list without this reads as a pile. Material 3 has no
 * subheader of its own; [com.softistx.material.components.data.EntityHeader] is the top of a *page*.
 * The title is `TitleMedium` in M3's emphasised weight ([emphasized], on by default), so a section
 * reads as a heading and is announced as one.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: @Composable (() -> Unit)? = null,
    emphasized: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Typography(
                text = title,
                variant = TypographyVariant.TitleMedium,
                emphasized = emphasized,
                modifier = Modifier.semantics { heading() },
            )
            if (supporting != null) {
                Typography(text = supporting, emphasis = Emphasis.Medium)
            }
        }
        action?.invoke()
    }
}
