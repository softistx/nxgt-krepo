package com.strange.material.surface

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * A modal drawer. Material 3's `ModalNavigationDrawer`.
 *
 * The permanent rail is [com.strange.material.navigation.NavigationSuite]; this is the overlay that
 * a compact window opens from a menu icon. [open] is the caller's; the drawer animates itself.
 */
@Composable
fun Drawer(
    open: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    drawer: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberDrawerState(if (open) DrawerValue.Open else DrawerValue.Closed)
    LaunchedEffect(open) {
        if (open) state.open() else state.close()
    }
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == DrawerValue.Closed && open) onDismiss()
    }
    ModalNavigationDrawer(
        drawerContent = { ModalDrawerSheet(content = drawer) },
        modifier = modifier,
        drawerState = state,
        gesturesEnabled = gesturesEnabled,
        content = content,
    )
}
