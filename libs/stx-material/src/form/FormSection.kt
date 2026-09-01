package com.softistx.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.display.SectionHeader
import com.softistx.material.theme.StrangeTheme

/**
 * A titled group of fields.
 *
 * [SectionHeader] is the title; this is the title plus the fields, spaced so a settings screen
 * does not invent a new gap between every heading and its first control.
 */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        SectionHeader(title = title, supporting = supporting)
        content()
    }
}
