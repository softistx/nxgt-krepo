package com.strange.material.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The icons this library draws with.
 *
 * They are defined here rather than taken from a pack because no pack is reachable: the Kotlin
 * Toolchain's `$compose` catalog has no key for the Material icons, `$compose.material` does not
 * carry `material-icons-core` in Compose Multiplatform 1.11, and the AndroidX icon artifacts are
 * Android-only. A small hand-held set is also the honest size for a design system — an application
 * that wants a thousand glyphs should depend on a pack directly and pass the [ImageVector] in.
 *
 * Every path is the 24×24 Material Symbols outline, so an application that later swaps in the real
 * pack gets the same shapes.
 */
object StrangeIcons {
    val Add: ImageVector = icon("Add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")

    val Check: ImageVector = icon("Check", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z")

    val Close: ImageVector =
        icon(
            "Close",
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 " +
                "13.41 17.59 19 19 17.59 13.41 12z",
        )

    val ChevronRight: ImageVector =
        icon("ChevronRight", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")

    val Delete: ImageVector =
        icon(
            "Delete",
            "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
        )

    val Edit: ImageVector =
        icon(
            "Edit",
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 " +
                "0-1.41l-2.34-2.34a.9959.9959 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
        )

    val Inbox: ImageVector =
        icon(
            "Inbox",
            "M19 3H4.99c-1.11 0-1.98.89-1.98 2L3 19c0 1.1.88 2 1.99 2H19c1.1 0 2-.9 " +
                "2-2V5c0-1.1-.9-2-2-2zm0 12h-4c0 1.66-1.35 3-3 3s-3-1.34-3-3H4.99V5H19v10z",
        )

    val Person: ImageVector =
        icon(
            "Person",
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 " +
                "0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
        )

    val Search: ImageVector =
        icon(
            "Search",
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 " +
                "3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 " +
                "4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 " +
                "14 9.5 11.99 14 9.5 14z",
        )

    val Warning: ImageVector =
        icon("Warning", "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z")
}

/**
 * The fill is black because it is never seen: `Icon` tints the vector with the content colour, so
 * the declared fill only matters to a caller that draws the vector itself.
 */
private fun icon(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
        .build()
