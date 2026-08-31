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

    val ChevronUp: ImageVector =
        icon("ChevronUp", "M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z")

    val ChevronLeft: ImageVector =
        icon("ChevronLeft", "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z")

    val ChevronRight: ImageVector =
        icon("ChevronRight", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")

    val ChevronDown: ImageVector =
        icon("ChevronDown", "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z")

    /** The reveal button on a secret field, and its struck-through twin. */
    val Eye: ImageVector =
        icon(
            "Eye",
            "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zm0 " +
                "12.5c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 " +
                "3 3-1.34 3-3-1.34-3-3-3z",
        )

    val EyeOff: ImageVector =
        icon(
            "EyeOff",
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11" +
                "-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 " +
                "10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM12 " +
                "17c-2.76 0-5-2.24-5-5 0-.77.18-1.5.49-2.14l1.57 1.57c-.03.18-.06.37-.06.57 0 1.66 1.34 3 3 3 .2 0 " +
                ".38-.03.57-.07L14.14 16.5c-.65.31-1.37.5-2.14.5z",
        )

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

    val Home: ImageVector =
        icon("Home", "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z")

    val Menu: ImageVector =
        icon("Menu", "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z")

    val MoreHoriz: ImageVector =
        icon(
            "MoreHoriz",
            "M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 " +
                "2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
        )

    val Calendar: ImageVector =
        icon(
            "Calendar",
            "M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 " +
                "2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zM9 14H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z",
        )

    val Schedule: ImageVector =
        icon(
            "Schedule",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 " +
                "11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 " +
                "3.15.75-1.23-4.5-2.67z",
        )

    val Star: ImageVector =
        icon(
            "Star",
            "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.28-.61L12 2 9.28 8.63 2 9.24l5.46 " +
                "4.73L5.82 21z",
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

    val Copy: ImageVector =
        icon(
            "Copy",
            "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 " +
                "2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
        )

    val Minus: ImageVector = icon("Minus", "M19 13H5v-2h14v2z")

    val Attach: ImageVector =
        icon(
            "Attach",
            "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-3 2.5-3s2.5 1.62 " +
                "2.5 3v10.5c0 .55-.45 1-1 1s-1-.45-1-1V6H10v9.5c0 1.38 1.12 3 2.5 3s2.5-1.62 " +
                "2.5-3V5c0-2.21-1.79-4-4-4S7 2.79 7 5v12.5c0 3.04 2.46 5.5 5.5 5.5s5.5-2.46 " +
                "5.5-5.5V6h-1.5z",
        )

    val Info: ImageVector =
        icon(
            "Info",
            "M11 17h2v-6h-2v6zm1-15C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 " +
                "12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zM11 9h2V7h-2v2z",
        )

    val ViewList: ImageVector =
        icon(
            "ViewList",
            "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 " +
                "7v2h14V7H7z",
        )

    val ViewGrid: ImageVector =
        icon(
            "ViewGrid",
            "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4z" +
                "m-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z",
        )
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
