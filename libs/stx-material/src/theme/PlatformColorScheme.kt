package com.strange.material.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Where the Material 3 scheme comes from on this platform.
 *
 * Only this one decision is platform-specific — Android can read the user's wallpaper colours from
 * API 31, and nothing else can. [StrangeThemeProvider] and everything above it are written once.
 *
 * @param seed the brand colour, used whenever the platform has nothing better
 * @param dynamicColor whether to prefer the platform's own palette; ignored where there is none
 */
@Composable
expect fun platformColorScheme(
    seed: Color,
    isDark: Boolean,
    dynamicColor: Boolean = true,
): ColorScheme

/**
 * Whether [platformColorScheme] can answer with something other than the seed.
 *
 * Public because it is the honest way for a settings screen to decide whether to *offer* the
 * choice, rather than showing a switch that does nothing.
 */
expect val supportsDynamicColor: Boolean
