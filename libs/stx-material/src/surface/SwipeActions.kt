package com.strange.material.surface

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A row that reveals actions on a swipe. Material 3's `SwipeToDismissBox`.
 *
 * [background] is the reveal (usually a danger fill and a delete icon). [onDismiss] fires once the
 * swipe settles past the threshold. The content is the row the reader sees at rest.
 */
@Composable
fun SwipeActions(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    background: @Composable RowScope.() -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        backgroundContent = background,
        modifier = modifier,
        onDismiss = { onDismiss() },
        content = content,
    )
}
