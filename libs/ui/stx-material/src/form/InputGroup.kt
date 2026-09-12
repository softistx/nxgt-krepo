package com.softistx.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.theme.StxTheme

/**
 * A field and the control that acts on it, on one line — a search box and its button, an amount and
 * its currency, a code and *Apply*.
 *
 * It is a layout and nothing else: no state, no styling, no assumptions about what is inside. The
 * field takes the room that is left, which is the only rule the arrangement actually needs, and
 * everything is bottom-aligned so a field carrying an error message does not drag its button down
 * with it.
 */
@Composable
fun InputGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
        verticalAlignment = Alignment.Bottom,
        content = content,
    )
}
