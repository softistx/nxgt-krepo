package com.softistx.material.components.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/**
 * The top of a detail page: identity, a line of meta, and the actions that apply to it. The title
 * is `HeadlineMedium` in M3's emphasised weight ([emphasized], on by default): the page's one
 * emphasised title.
 */
@Composable
fun EntityHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    emphasized: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Typography(text = title, variant = TypographyVariant.HeadlineMedium, emphasized = emphasized)
            if (supporting != null) {
                Typography(text = supporting, emphasis = Emphasis.Medium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs), content = actions)
    }
}
