package com.softistx.material.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Desktop has no system palette to borrow, so the seed is the only source. */
actual val supportsDynamicColor: Boolean = false

/** Always the seed. [dynamicColor] is accepted and ignored, so callers stay platform-agnostic. */
@Composable
actual fun platformColorScheme(
    seed: Color,
    isDark: Boolean,
    dynamicColor: Boolean,
): ColorScheme = remember(seed, isDark) { strangeColorScheme(seed, isDark) }
