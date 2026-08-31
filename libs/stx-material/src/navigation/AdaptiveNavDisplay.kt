package com.strange.material.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.strange.material.theme.StrangeTheme

/**
 * A [NavDisplay] already wired for Material 3 Adaptive's two canonical scenes.
 *
 * List-detail and supporting-pane are tried in that order; anything they decline falls through to
 * Navigation 3's single pane. Callers mark entries with [ListDetail] and [SupportingPane] metadata
 * — they never construct a `SceneStrategy`.
 *
 * Transitions are a fade on the *effects* axis. A spatial spring here would overshoot the scene
 * size, which Adaptive's own pane motion already owns.
 */
@Composable
fun <T : Any> AdaptiveNavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {
        if (backStack is MutableList<T>) {
            backStack.removeLastOrNull()
        }
    },
    entryProvider: (T) -> NavEntry<T>,
) {
    val listDetail = rememberListDetailSceneStrategy<T>()
    val supporting = rememberSupportingPaneSceneStrategy<T>()
    val fade = StrangeTheme.motion.effects<Float>()
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        sceneStrategies = listOf(listDetail, supporting),
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        popTransitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        entryProvider = entryProvider,
    )
}
