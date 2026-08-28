package com.strange.material.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.strange.material.theme.DefaultSeed

/**
 * What the reader has chosen: the story on screen, and the two theme dials the catalogue drives
 * `StrangeTheme` with. Nothing here is per-component — a story that needs its own state keeps it
 * in its own `remember`.
 */
@Stable
class CatalogState(
    initialStoryId: String,
) {
    var storyId by mutableStateOf(initialStoryId)
    var isDark by mutableStateOf(false)
    var seed by mutableStateOf(DefaultSeed)

    /** True once the reader has opened a story on a layout that shows one pane at a time. */
    var opened by mutableStateOf(false)
}

@Composable
fun rememberCatalogState(initialStoryId: String): CatalogState = remember { CatalogState(initialStoryId) }

/**
 * The seeds the picker offers. `StrangeTheme` accepts any [Color]; these are the ones worth one
 * click, chosen to show that every component repaints from the seed rather than from a constant.
 */
val CatalogSeeds: List<Pair<String, Color>> =
    listOf(
        "Indigo" to DefaultSeed,
        "Teal" to Color(0xFF00796B),
        "Amber" to Color(0xFFB8860B),
        "Rose" to Color(0xFFC2185B),
        "Slate" to Color(0xFF475569),
    )
