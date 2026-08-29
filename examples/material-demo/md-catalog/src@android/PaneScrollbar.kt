package com.strange.material.demo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A finger already knows the list moves, and a permanent bar beside it is clutter. */
@Composable
actual fun PaneScrollbar(
    state: LazyListState,
    modifier: Modifier,
) = Unit

@Composable
actual fun PaneScrollbar(
    state: ScrollState,
    modifier: Modifier,
) = Unit
