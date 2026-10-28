package com.softistx.material.components.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.components.content.EmptyState
import com.softistx.material.components.content.EmptyStateDefaults
import com.softistx.material.foundation.icon.StxIcons

/**
 * A list and its detail side by side when the width allows, one at a time when it does not.
 *
 * It measures the width it is offered (like `ResponsiveButton` and `ResponsiveGrid`), not the
 * window, so it folds inside a narrow destination or a narrow desktop window, and a headless render
 * at any size sees the layout a person would. Selection is plain state: [selected] says whether a
 * detail is open, and the caller owns what it is.
 *
 * - **Wide** (offered width at least [wideThreshold]): [list] is [listWidth] wide, a divider, then
 *   [detail] when [selected], else [placeholder] in the second pane.
 * - **Narrow**: [detail] fills the space when [selected], else [list] does.
 *
 * [detail] runs only when [selected] is true. Each pane gets the [Modifier] it must fill.
 *
 * In the single-pane layout nothing leads the reader back from the detail to the list: back
 * handling stays the caller's (a back button or `BackHandler` that clears the selection).
 *
 * Not to be confused with [com.softistx.material.components.navigation.AdaptiveNavDisplay] and the
 * `ListDetail` scene metadata: those follow the window size class and a Navigation 3 back stack.
 * Use this one to fold inside a destination or a narrow window, with no back stack.
 *
 * It takes no `style`: a layout is inert and has no look of its own (the divider and the
 * placeholder are Material 3's and [EmptyState]'s), like `StateHost`.
 */
@Composable
fun ListDetailLayout(
    selected: Boolean,
    placeholderTitle: String,
    modifier: Modifier = Modifier,
    wideThreshold: Dp = ListDetailLayoutDefaults.WideThreshold,
    listWidth: Dp = ListDetailLayoutDefaults.ListWidth,
    placeholder: @Composable (Modifier) -> Unit = { ListDetailLayoutDefaults.Placeholder(placeholderTitle, it) },
    list: @Composable (Modifier) -> Unit,
    detail: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= wideThreshold) {
            Row(modifier = Modifier.fillMaxSize()) {
                list(Modifier.width(listWidth).fillMaxHeight())
                VerticalDivider()
                val pane = Modifier.weight(1f).fillMaxHeight()
                if (selected) detail(pane) else placeholder(pane)
            }
        } else if (selected) {
            detail(Modifier.fillMaxSize())
        } else {
            list(Modifier.fillMaxSize())
        }
    }
}

/** The defaults of [ListDetailLayout]. */
object ListDetailLayoutDefaults {
    /** The offered width from which two panes are shown. */
    val WideThreshold: Dp = 720.dp

    /** The list pane's width in the two-pane layout. */
    val ListWidth: Dp = 380.dp

    /** The empty second pane: an [EmptyState] with a touch icon and [title]. */
    @Composable
    fun Placeholder(
        title: String,
        modifier: Modifier = Modifier,
    ) {
        EmptyState(
            title = title,
            illustration = { EmptyStateDefaults.IconPlate(StxIcons.Touch) },
            modifier = modifier,
        )
    }
}
