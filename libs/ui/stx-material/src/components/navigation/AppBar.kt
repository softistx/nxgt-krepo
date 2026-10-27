package com.softistx.material.components.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.softistx.material.components.button.ButtonColor
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.IconButton
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * How tall the bar is, mapped onto Material 3's top app bars.
 *
 * [Small] and [Centered] are M3's plain bars. [Medium] and [Large] are its *flexible* bars: the
 * title sits under the actions in the expressive headline type and, given a `scrollBehavior`,
 * collapses into the small bar as the content scrolls. [Centered] is the one a detail screen
 * usually wants; [Large] is the one a list screen wants when it will scroll.
 */
enum class AppBarSize {
    Small,
    Centered,
    Medium,
    Large,
}

/**
 * The bar at the top of a screen.
 *
 * ```kotlin
 * AppBar("Orders", navigationIcon = StxIcons.Menu, onNavigation = { /* open */ })
 * ```
 *
 * [title] is a string because that is the common case; [actions] is a slot because a bar's trailing
 * side is almost never one thing. The navigation icon is an [IconButton] so it keeps the 48 dp
 * target and the ripple — a clickable vector in the slot would throw both away.
 *
 * The title is emphasised and announced as a heading. A [Medium] or [Large] bar takes the type of
 * M3's flexible bar for its expanded title, so it is larger than the small one and wraps instead of
 * truncating; pass [scrollBehavior] (`TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`) to
 * make it collapse; a [Small] or [Centered] bar given `enterAlwaysScrollBehavior()` slides away on
 * scroll and returns on the first scroll back. The bar itself has no interaction state (the
 * navigation button keeps its own style), so it takes no `style`.
 */
@Composable
fun AppBar(
    title: String,
    modifier: Modifier = Modifier,
    size: AppBarSize = AppBarSize.Small,
    navigationIcon: ImageVector? = null,
    navigationDescription: String? = null,
    onNavigation: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val nav: @Composable () -> Unit = {
        if (navigationIcon != null && onNavigation != null) {
            IconButton(
                icon = navigationIcon,
                description = navigationDescription,
                onClick = onNavigation,
                variant = ButtonVariant.Ghost,
                color = ButtonColor.Neutral,
            )
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors()
    when (size) {
        AppBarSize.Small -> {
            TopAppBar(
                title = { BarTitle(title) },
                modifier = modifier,
                navigationIcon = nav,
                actions = actions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
        }

        AppBarSize.Centered -> {
            CenterAlignedTopAppBar(
                title = { BarTitle(title) },
                modifier = modifier,
                navigationIcon = nav,
                actions = actions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
        }

        AppBarSize.Medium -> {
            MediumFlexibleTopAppBar(
                title = { ExpandedTitle(title) },
                modifier = modifier,
                navigationIcon = nav,
                actions = actions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
        }

        AppBarSize.Large -> {
            LargeFlexibleTopAppBar(
                title = { ExpandedTitle(title) },
                modifier = modifier,
                navigationIcon = nav,
                actions = actions,
                colors = colors,
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

/** The one line of a small bar: the emphasised title, kept to one line and announced as a heading. */
@Composable
private fun BarTitle(title: String) {
    Typography(
        text = title,
        variant = TypographyVariant.TitleLarge,
        emphasized = true,
        maxLines = 1,
        modifier = Modifier.semantics { heading() },
    )
}

/** The title of a flexible bar. It sets no style: the bar hands over its expanded and collapsed type. */
@Composable
private fun ExpandedTitle(title: String) {
    Text(text = title, modifier = Modifier.semantics { heading() })
}
