package com.softistx.material.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

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
 *
 * Compact drops section headers, supporting text, chips and shortcuts. Badge and unread dot stay:
 * they are why M3's item has a badge slot.
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
    val compact = LocalWindowInfo.current.containerDpSize.width < 600.dp
    NavigationSuiteScaffold(
        navigationItems = {
            var previousSection: String? = null
            destinations.forEachIndexed { index, destination ->
                if (destination.showSection(compact, previousSection)) {
                    Typography(
                        text = destination.section!!,
                        variant = TypographyVariant.Overline,
                        emphasis = Emphasis.Subtle,
                        modifier =
                            Modifier.padding(
                                start = StxTheme.spacing.md,
                                top = StxTheme.spacing.md,
                                bottom = StxTheme.spacing.xs,
                            ),
                    )
                }
                previousSection = destination.section
                val badge = destination.resolvedBadge()
                NavigationSuiteItem(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    icon = { DestinationLeading(destination, selected = index == selected) },
                    label = { DestinationLabel(destination, compact) },
                    enabled = destination.enabled,
                    badge =
                        if (badge is ResolvedBadge.None) {
                            null
                        } else {
                            { DestinationBadge(destination) }
                        },
                )
            }
        },
        modifier = modifier,
        primaryActionContent = primaryAction ?: {},
        content = content,
    )
}
