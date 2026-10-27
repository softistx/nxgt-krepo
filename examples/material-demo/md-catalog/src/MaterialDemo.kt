package com.softistx.material.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.motion.StxMotion
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.platformColorScheme

/**
 * The whole demo. Both launchers call this and nothing else, so the Android application and the
 * desktop window can never show different catalogues.
 */
@Composable
fun MaterialDemo(groups: List<StoryGroup> = CatalogGroups) {
    val state =
        rememberCatalogState(
            initialStoryId =
                groups
                    .first()
                    .stories
                    .first()
                    .id,
        )
    // The catalogue calls StxTheme directly because it drives all four of Material 3's inputs
    // from the header. An ordinary application writes `StxThemeProvider(seed = …)` instead —
    // one line, and the platform decides where the scheme comes from.
    StxTheme(
        isDark = state.isDark,
        colorScheme =
            platformColorScheme(
                seed = state.seed,
                isDark = state.isDark,
                dynamicColor = state.dynamicColor,
                paletteStyle = state.paletteStyle,
            ),
        motionScheme = state.motion.scheme,
        motion = StxMotion(state.motion.scheme, enabled = state.motion.enabled),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            CatalogScaffold(state = state, groups = groups)
        }
    }
}
