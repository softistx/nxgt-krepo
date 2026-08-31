package com.strange.material.button

import androidx.compose.material3.AppBarOverflowIndicator
import androidx.compose.material3.AppBarRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.strange.material.icon.Icon

/** One action in an [OverflowBar]. */
@Immutable
data class OverflowAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * A row of icon actions that collapses into "more". Material 3's `AppBarRow`.
 *
 * Visible icons stay in the row; the rest land in the overflow menu that [AppBarOverflowIndicator]
 * opens. [maxVisible] is a cap on top of that — a toolbar that must keep three icons on a wide
 * window still uses the same list.
 */
@Composable
fun OverflowBar(
    actions: List<OverflowAction>,
    modifier: Modifier = Modifier,
    maxVisible: Int = Int.MAX_VALUE,
) {
    AppBarRow(
        modifier = modifier,
        overflowIndicator = { AppBarOverflowIndicator(it) },
        maxItemCount = maxVisible,
    ) {
        actions.forEach { action ->
            clickableItem(
                onClick = action.onClick,
                icon = { Icon(icon = action.icon, description = action.label) },
                label = action.label,
                enabled = action.enabled,
            )
        }
    }
}
