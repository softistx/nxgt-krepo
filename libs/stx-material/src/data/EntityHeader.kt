package com.softistx.material.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * The top of a detail page: identity, a line of meta, and the actions that apply to it.
 */
@Composable
fun EntityHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Typography(text = title, variant = TypographyVariant.HeadlineSmall)
            if (supporting != null) {
                Typography(text = supporting, emphasis = Emphasis.Medium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs), content = actions)
    }
}
