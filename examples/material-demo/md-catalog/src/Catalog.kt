package com.strange.material.demo

import com.strange.material.demo.stories.ButtonStories
import com.strange.material.demo.stories.DisplayStories
import com.strange.material.demo.stories.FormStories
import com.strange.material.demo.stories.FoundationStories
import com.strange.material.demo.stories.MotionStories
import com.strange.material.demo.stories.ScreenStories

/**
 * Every group the catalogue shows, in reading order: what the library is built from, then what it
 * is built into, then a whole screen.
 *
 * A component added in any phase registers its story in the same change — the catalogue is never
 * caught up with afterwards.
 */
val CatalogGroups: List<StoryGroup> =
    listOf(
        FoundationStories,
        MotionStories,
        ButtonStories,
        DisplayStories,
        FormStories,
        ScreenStories,
    )
