package com.softistx.material.components.navigation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography

/**
 * A row of tabs. Material 3's `PrimaryTabRow` (or the scrollable one), which already owns the
 * indicator, the equal-width layout and the selected semantics.
 *
 * ```kotlin
 * Tabs(listOf("Paid", "Pending", "Refunded"), selected, onSelect)
 * ```
 *
 * [scrollable] is for a set that will not fit; leaving it false on a long list is how tabs overflow
 * the row rather than becoming a scroller, and that is a decision the caller has to make.
 */
@Composable
fun Tabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
    scrollable: Boolean = false,
    enabled: Boolean = true,
    style: Style = Style,
) {
    val tabs: @Composable () -> Unit = {
        labels.forEachIndexed { index, label ->
            // The press is per tab: a row-level source has no child feeding it, so its style never fired.
            val interactionSource = remember { MutableInteractionSource() }
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                modifier =
                    Modifier.styledBy(
                        style,
                        enabled = enabled,
                        base = navigationItemStyle,
                        interactionSource = interactionSource,
                        selected =
                            index == selected,
                    ),
                enabled = enabled,
                interactionSource = interactionSource,
                text = { Typography(text = label) },
                icon =
                    icons?.getOrNull(index)?.let { image ->
                        { Icon(icon = image, description = null) }
                    },
            )
        }
    }
    if (scrollable) {
        PrimaryScrollableTabRow(selectedTabIndex = selected, modifier = modifier, tabs = tabs)
    } else {
        PrimaryTabRow(selectedTabIndex = selected, modifier = modifier, tabs = tabs)
    }
}
