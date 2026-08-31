package com.strange.material.navigation

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.strange.material.display.StatusBadge
import com.strange.material.icon.Icon
import com.strange.material.text.Typography
import com.strange.material.theme.Tone

/**
 * One destination in a [NavigationSuite].
 *
 * The suite picks bar, rail or drawer from the window; the destination is only what to show and
 * whether it is current. [badge] is a short label ("3", "new") drawn with [StatusBadge] — M3's
 * item already has the slot, so this is the tone sitting in it, not a second badge.
 */
@Immutable
data class NavigationDestination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector? = null,
    val badge: String? = null,
    val badgeTone: Tone = Tone.Info,
    val enabled: Boolean = true,
)

/**
 * The chrome around an application: a bar, a rail or a drawer, from one list of destinations.
 *
 * ```kotlin
 * NavigationSuite(destinations, selected, onSelect) { Inbox() }
 * ```
 *
 * Material 3 Adaptive's `NavigationSuiteScaffold` is the thing that morphs. This is the vocabulary
 * in front of it, so a caller never writes three layouts and a `when` on width. [primaryAction]
 * is the FAB that the suite places in the rail header or above the bar, depending on which it is.
 */
@Composable
fun NavigationSuite(
    destinations: List<NavigationDestination>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    primaryAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationItems = {
            destinations.forEachIndexed { index, destination ->
                NavigationSuiteItem(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(
                            icon =
                                if (index == selected) {
                                    destination.selectedIcon ?: destination.icon
                                } else {
                                    destination.icon
                                },
                            description = null,
                        )
                    },
                    label = { Typography(text = destination.label) },
                    enabled = destination.enabled,
                    badge =
                        destination.badge?.let { text ->
                            { StatusBadge(text = text, tone = destination.badgeTone) }
                        },
                )
            }
        },
        modifier = modifier,
        primaryActionContent = primaryAction ?: {},
        content = content,
    )
}
