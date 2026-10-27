package com.softistx.material.components.navigation

import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import com.softistx.material.components.content.EmptyState

/**
 * Metadata for [AdaptiveNavDisplay] list-detail scenes.
 *
 * These are the same maps [ListDetailSceneStrategy] already understands — wrapping them is how a
 * caller gets this library's empty detail (an [EmptyState]) instead of a blank pane, without
 * importing the adaptive-navigation3 package.
 */
object ListDetail {
    fun list(
        placeholderTitle: String = "Select an item",
        placeholderDescription: String? = null,
    ): Map<String, Any> =
        ListDetailSceneStrategy.listPane {
            EmptyState(title = placeholderTitle, description = placeholderDescription)
        }

    fun detail(): Map<String, Any> = ListDetailSceneStrategy.detailPane()

    fun extra(): Map<String, Any> = ListDetailSceneStrategy.extraPane()
}
