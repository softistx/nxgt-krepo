package com.strange.material.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Material You landed in Android 12; below that the wallpaper palette does not exist. */
actual val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The wallpaper's palette when the device has one and the caller wants it, the seed otherwise.
 *
 * The branch is inside the `remember` rather than around it: a conditional composable call would
 * make the two paths different slots, and switching the toggle at runtime would then discard the
 * subtree instead of recolouring it.
 */
@Composable
actual fun platformColorScheme(
    seed: Color,
    isDark: Boolean,
    dynamicColor: Boolean,
): ColorScheme {
    val context = LocalContext.current
    return remember(seed, isDark, dynamicColor, context) {
        when {
            !dynamicColor || !supportsDynamicColor -> strangeColorScheme(seed, isDark)
            isDark -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context)
        }
    }
}
