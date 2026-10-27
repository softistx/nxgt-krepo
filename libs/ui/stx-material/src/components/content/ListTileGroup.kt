package com.softistx.material.components.content

import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.unit.Dp

/**
 * Where a [ListTile] sits in a segmented list: the [index]-th of [count]. The first tile has round
 * top corners, the last round bottom corners, and the ones between are squarer on both sides, so a
 * run of tiles reads as one block. Space them by [ListTileDefaults.GroupGap].
 */
data class ListTileGroup(
    val index: Int,
    val count: Int,
) {
    init {
        require(count >= 1) { "count must be at least 1, was $count" }
        require(index in 0 until count) { "index $index is outside 0 until $count" }
    }
}

/** What a segmented run of [ListTile]s is built from. */
object ListTileDefaults {
    /** The vertical space M3 puts between the tiles of a segmented list. */
    val GroupGap: Dp get() = ListItemDefaults.SegmentedGap
}
