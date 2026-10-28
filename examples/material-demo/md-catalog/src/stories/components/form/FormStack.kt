package com.softistx.material.demo.stories.components.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.theme.StxTheme

/** Every story here is a column of fields; this is that column, once. */
@Composable
internal fun Stack(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
    ) {
        content()
    }
}
