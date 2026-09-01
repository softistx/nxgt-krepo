package com.softistx.material.demo

import com.softistx.material.demo.stories.ButtonStories
import com.softistx.material.demo.stories.DataStories
import com.softistx.material.demo.stories.DateTimeStories
import com.softistx.material.demo.stories.DisplayStories
import com.softistx.material.demo.stories.FormStories
import com.softistx.material.demo.stories.FoundationStories
import com.softistx.material.demo.stories.LayoutStories
import com.softistx.material.demo.stories.MediaStories
import com.softistx.material.demo.stories.MotionStories
import com.softistx.material.demo.stories.NavigationStories
import com.softistx.material.demo.stories.ScreenStories
import com.softistx.material.demo.stories.SurfaceStories

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
        NavigationStories,
        LayoutStories,
        SurfaceStories,
        DateTimeStories,
        DataStories,
        MediaStories,
        ScreenStories,
    )
