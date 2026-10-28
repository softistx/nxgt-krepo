package com.softistx.material.components.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
 * The default [placeholder] reads "Select an item"; for another text write
 * `placeholder = { ListDetailLayoutDefaults.Placeholder("Select a student", it) }`.
 *
 * [detail] runs only when [selected] is true. Each pane gets the [Modifier] it must fill.
 *
 * In the single-pane layout nothing leads the reader back from the detail to the list: back
 * handling stays the caller's (a back button or `BackHandler` that clears the selection).
 *
 * A pane that is not shown leaves composition, but its `rememberSaveable` state is kept per pane
 * (a `SaveableStateHolder`), so a list's scroll position (`rememberLazyListState`) and a typed
 * draft survive the layout folding and a list, detail, list round trip. Plain `remember` state does
 * not: hoist that out of the panes. The panes carry no semantics of their own; label them (for example with `semantics { paneTitle }`
 * on the modifier you receive) if they should be announced.
 *
 * Not to be confused with [com.softistx.material.components.navigation.AdaptiveNavDisplay] and the
 * `ListDetail` scene metadata: those follow the window size class and a Navigation 3 back stack.
 * Use this one to fold inside a destination or a narrow window, with no back stack.
 *
 * It is built from primitives rather than Material 3's `ListDetailPaneScaffold`, which was checked
 * in the resolved adaptive-layout jar: it takes a `PaneScaffoldDirective` (the `Default` is
 * computed from `WindowAdaptiveInfo`, so from the window, not from the width offered here) and a
 * `ThreePaneScaffoldValue` or navigator state rather than a "selected" flag. Fitting it to an
 * offered width would mean building both by hand.
 *
 * It takes no `style`: a layout is inert and has no look of its own (the divider and the
 * placeholder are Material 3's and [EmptyState]'s), like `StateHost`.
 */
@Composable
fun ListDetailLayout(
    selected: Boolean,
    modifier: Modifier = Modifier,
    wideThreshold: Dp = ListDetailLayoutDefaults.WideThreshold,
    listWidth: Dp = ListDetailLayoutDefaults.ListWidth,
    placeholder: @Composable (Modifier) -> Unit = { ListDetailLayoutDefaults.Placeholder("Select an item", it) },
    list: @Composable (Modifier) -> Unit,
    detail: @Composable (Modifier) -> Unit,
) {
    val panes = rememberSaveableStateHolder()
    val currentList by rememberUpdatedState(list)
    val currentDetail by rememberUpdatedState(detail)
    // Movable, so a pane that changes place in a fold is moved, not disposed and rebuilt: the
    // saveable holder alone restores a key only after the old copy is gone, too late for the new one.
    val listPane = remember { movableContentOf { m: Modifier -> panes.SaveableStateProvider("list") { currentList(m) } } }
    val detailPane = remember { movableContentOf { m: Modifier -> panes.SaveableStateProvider("detail") { currentDetail(m) } } }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= wideThreshold) {
            Row(modifier = Modifier.fillMaxSize()) {
                listPane(Modifier.width(listWidth).fillMaxHeight())
                VerticalDivider()
                val pane = Modifier.weight(1f).fillMaxHeight()
                if (selected) detailPane(pane) else placeholder(pane)
            }
        } else if (selected) {
            detailPane(Modifier.fillMaxSize())
        } else {
            listPane(Modifier.fillMaxSize())
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
