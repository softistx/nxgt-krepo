package com.softistx.material.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.BottomAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Actions along the bottom of a screen, with an optional FAB. Material 3's `BottomAppBar`.
 *
 * Not [NavigationSuite]: that one *is* the destinations. This one is Edit, Share, Delete — the
 * verbs that apply to what is on the screen. [fab] is the one action that floats off the end.
 */
@Composable
fun BottomBar(
    modifier: Modifier = Modifier,
    fab: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    if (fab == null) {
        BottomAppBar(modifier = modifier, content = actions)
    } else {
        BottomAppBar(
            actions = actions,
            modifier = modifier,
            floatingActionButton = fab,
        )
    }
}
