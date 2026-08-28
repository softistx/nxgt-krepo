package com.strange.material.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.theme.StrangeTheme

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
    StrangeTheme(seed = state.seed, isDark = state.isDark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            CatalogScaffold(state = state, groups = groups)
        }
    }
}
