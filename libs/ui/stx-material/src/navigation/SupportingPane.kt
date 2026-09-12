package com.softistx.material.navigation

import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy

/**
 * Metadata for [AdaptiveNavDisplay] supporting-pane scenes.
 *
 * Main is the thing being read; supporting is the inspector, the comments, the knobs. Extra is the
 * third pane when the window can take it.
 */
object SupportingPane {
    fun main(): Map<String, Any> = SupportingPaneSceneStrategy.mainPane()

    fun supporting(): Map<String, Any> = SupportingPaneSceneStrategy.supportingPane()

    fun extra(): Map<String, Any> = SupportingPaneSceneStrategy.extraPane()
}
