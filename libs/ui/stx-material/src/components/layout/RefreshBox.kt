package com.softistx.material.components.layout

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.style.Style
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy

/**
 * Pull to refresh. Material 3's `PullToRefreshBox`.
 *
 * [refreshing] is the caller's: this does not own a load. The indicator is M3's, so [style] has no
 * default look: it dresses the box the gesture is read on.
 */
@Composable
fun RefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.styledBy(style),
        content = content,
    )
}
