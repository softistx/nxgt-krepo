package com.softistx.material.components.surface

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.style.Style
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy

/**
 * A modal drawer. Material 3's `ModalNavigationDrawer`.
 *
 * The permanent rail is [com.softistx.material.components.navigation.NavigationSuite]; this is the overlay that
 * a compact window opens from a menu icon. [open] is the caller's; the drawer animates itself.
 * [style] has no default look: M3 paints the sheet's container over a background. It sees the
 * drawer as disabled while [gesturesEnabled] is false.
 */
@Composable
fun Drawer(
    open: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    drawer: @Composable ColumnScope.() -> Unit,
    style: Style = Style,
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
        modifier = modifier.styledBy(style, enabled = gesturesEnabled),
        drawerState = state,
        gesturesEnabled = gesturesEnabled,
        content = content,
    )
}
