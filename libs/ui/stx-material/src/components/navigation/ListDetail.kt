package com.softistx.material.components.navigation

import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import com.softistx.material.components.content.EmptyState
import com.softistx.material.foundation.theme.LocalStxStrings

/**
 * Metadata for [AdaptiveNavDisplay] list-detail scenes.
 *
 * These are the same maps [ListDetailSceneStrategy] already understands — wrapping them is how a
 * caller gets this library's empty detail (an [EmptyState]) instead of a blank pane, without
 * importing the adaptive-navigation3 package. A null [placeholderTitle] reads the catalogue's `selectAnItem`.
 */
object ListDetail {
    fun list(
        placeholderTitle: String? = null,
        placeholderDescription: String? = null,
    ): Map<String, Any> =
        ListDetailSceneStrategy.listPane {
            EmptyState(title = placeholderTitle ?: LocalStxStrings.current.selectAnItem, description = placeholderDescription)
        }

    fun detail(): Map<String, Any> = ListDetailSceneStrategy.detailPane()

    fun extra(): Map<String, Any> = ListDetailSceneStrategy.extraPane()
}
