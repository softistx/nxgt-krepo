package com.softistx.material.school

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import com.softistx.material.foundation.motion.StxMotion
import com.softistx.material.foundation.theme.ColorMode
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.platformColorScheme
import com.softistx.material.school.session.Preferences

/** Installs the theme the settings screen chose: light, dark or the system's, from the seed. */
@Composable
fun SchoolTheme(
    preferences: Preferences,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (preferences.colorMode) {
            ColorMode.Light -> false
            ColorMode.Dark -> true
            ColorMode.System -> isSystemInDarkTheme()
        }
    StxTheme(
        isDark = isDark,
        colorScheme =
            platformColorScheme(
                seed = preferences.seed,
                isDark = isDark,
                dynamicColor = preferences.dynamicColor,
            ),
        motion = StxMotion(MotionScheme.expressive(), enabled = preferences.animated),
        content = content,
    )
}
