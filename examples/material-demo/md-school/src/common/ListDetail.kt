package com.softistx.material.school.common

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
import com.softistx.material.foundation.icon.StxIcons

private val WideThreshold = 720.dp

/**
 * List and detail side by side when the width allows, one at a time when it does not. It measures
 * the width it is offered, like `ResponsiveButton` does, so it folds inside a narrow window on a
 * desktop and a headless render at any size sees the same layout a person would.
 *
 * [detail] runs only when something is selected; [placeholder] fills the empty second pane.
 */
@Composable
fun ListDetail(
    selected: Boolean,
    placeholderTitle: String,
    modifier: Modifier = Modifier,
    list: @Composable (Modifier) -> Unit,
    detail: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth.isWide()) {
            Row(modifier = Modifier.fillMaxSize()) {
                list(Modifier.width(380.dp).fillMaxHeight())
                VerticalDivider()
                if (selected) {
                    detail(Modifier.weight(1f).fillMaxHeight())
                } else {
                    EmptyState(title = placeholderTitle, icon = StxIcons.Inbox, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else if (selected) {
            detail(Modifier.fillMaxSize())
        } else {
            list(Modifier.fillMaxSize())
        }
    }
}

/** True when [this] width has room for two panes. Screens that lay out columns ask the same question. */
fun Dp.isWide(): Boolean = this >= WideThreshold
