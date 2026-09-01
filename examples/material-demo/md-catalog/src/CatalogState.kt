package com.softistx.material.demo

import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.softistx.material.theme.DefaultSeed

/**
 * What the reader has chosen: the story on screen, and the two theme dials the catalogue drives
 * `StxTheme` with. Nothing here is per-component — a story that needs its own state keeps it
 * in its own `remember`.
 */
@Stable
class CatalogState(
    initialStoryId: String,
) {
    var storyId by mutableStateOf(initialStoryId)
    var isDark by mutableStateOf(false)
    var seed by mutableStateOf(DefaultSeed)

    /** Whether to prefer the platform's own palette where there is one. Android 12+ only. */
    var dynamicColor by mutableStateOf(false)

    /** Which Material 3 motion scheme the whole tree animates with, or none at all. */
    var motion by mutableStateOf(CatalogMotion.Expressive)

    /** True once the reader has opened a story on a layout that shows one pane at a time. */
    var opened by mutableStateOf(false)
}

@Composable
fun rememberCatalogState(initialStoryId: String): CatalogState = remember { CatalogState(initialStoryId) }

/**
 * The three ways the catalogue can animate. `Off` stops *this library's* motion — Material 3's own
 * components keep their built-in animations, because a `MotionScheme` has no null.
 */
enum class CatalogMotion(
    val label: String,
    val scheme: MotionScheme,
    val enabled: Boolean,
) {
    Expressive("Expressive", MotionScheme.expressive(), enabled = true),
    Standard("Standard", MotionScheme.standard(), enabled = true),
    Off("Off", MotionScheme.standard(), enabled = false),
}

/**
 * The seeds the picker offers. `StxTheme` accepts any [Color]; these are the ones worth one
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
