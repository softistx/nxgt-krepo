package com.softistx.material.components.surface

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.style.Style
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.softistx.material.foundation.style.styledBy

/**
 * A row that reveals actions on a swipe. Material 3's `SwipeToDismissBox`.
 *
 * [background] is the reveal (usually a danger fill and a delete icon). [onDismiss] fires once the
 * swipe settles past the threshold. The content is the row the reader sees at rest.
 *
 * A swipe is a gesture a screen reader or a keyboard cannot make: [customActions] are the same
 * actions as named, labelled entries in the row's actions menu. The labels are the caller's, since
 * only the caller knows what the reveal does ("Delete", "Archive"). [style] has no default look.
 */
@Composable
fun SwipeActions(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    background: @Composable RowScope.() -> Unit,
    customActions: List<CustomAccessibilityAction> = emptyList(),
    style: Style = Style,
    content: @Composable RowScope.() -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        backgroundContent = background,
        modifier = modifier.styledBy(style).semantics { this.customActions = customActions },
        onDismiss = { onDismiss() },
        content = content,
    )
}
