package com.strange.material.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.strange.material.button.ButtonColor
import com.strange.material.button.ButtonVariant
import com.strange.material.button.IconButton
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant

/**
 * How tall the bar is, mapped onto Material 3's four top app bars.
 *
 * The four are M3's own composables — collapsing, insets, the two-line large title — so this is a
 * vocabulary in front of them, not a second bar. [Centered] is the one a detail screen usually
 * wants; [Large] is the one a list screen wants when it will scroll.
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
 * AppBar("Orders", navigationIcon = StrangeIcons.Menu, onNavigation = { /* open */ })
 * ```
 *
 * [title] is a string because that is the common case; [actions] is a slot because a bar's trailing
 * side is almost never one thing. The navigation icon is an [IconButton] so it keeps the 48 dp
 * target and the ripple — a clickable vector in the slot would throw both away.
 */
@Composable
fun AppBar(
    title: String,
    modifier: Modifier = Modifier,
    size: AppBarSize = AppBarSize.Small,
    navigationIcon: ImageVector? = null,
    navigationDescription: String? = null,
    onNavigation: (() -> Unit)? = null,
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
    val heading = @Composable { Typography(text = title, variant = TypographyVariant.TitleLarge) }
    val colors = TopAppBarDefaults.topAppBarColors()
    when (size) {
        AppBarSize.Small -> {
            TopAppBar(
                title = heading,
                modifier = modifier,
                navigationIcon = nav,
                actions = actions,
                colors = colors,
            )
        }

        AppBarSize.Centered -> {
            CenterAlignedTopAppBar(
                title = heading,
                modifier = modifier,
                navigationIcon = nav,
                actions = actions,
                colors = colors,
            )
        }

        AppBarSize.Medium -> {
            MediumTopAppBar(
                title = heading,
                modifier = modifier,
                navigationIcon = nav,
                actions = actions,
                colors = colors,
            )
        }

        AppBarSize.Large -> {
            LargeTopAppBar(
                title = heading,
                modifier = modifier,
                navigationIcon = nav,
                actions = actions,
                colors = colors,
            )
        }
    }
}
