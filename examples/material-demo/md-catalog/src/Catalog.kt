package com.softistx.material.demo

import com.softistx.material.demo.stories.components.button.ButtonStories
import com.softistx.material.demo.stories.components.content.ContentStories
import com.softistx.material.demo.stories.components.data.DataStories
import com.softistx.material.demo.stories.components.datetime.DateTimeStories
import com.softistx.material.demo.stories.components.feedback.FeedbackStories
import com.softistx.material.demo.stories.components.form.FormStories
import com.softistx.material.demo.stories.components.layout.LayoutStories
import com.softistx.material.demo.stories.components.media.MediaStories
import com.softistx.material.demo.stories.components.navigation.NavigationStories
import com.softistx.material.demo.stories.components.surface.SurfaceStories
import com.softistx.material.demo.stories.foundation.FoundationStories
import com.softistx.material.demo.stories.foundation.MotionStories
import com.softistx.material.demo.stories.screens.ScreenStories

/**
 * Every group the catalogue shows, in reading order: what the library is built from, then what it
 * is built into, then a whole screen.
 *
 * A component added in any phase registers its story in the same change — the catalogue is never
 * caught up with afterwards.
 */
val CatalogGroups: List<StoryGroup> =
    listOf(
        // Foundation: what the library is built from.
        FoundationStories,
        MotionStories,
        // Components, in the order of their families.
        ButtonStories,
        FormStories,
        NavigationStories,
        LayoutStories,
        FeedbackStories,
        ContentStories,
        SurfaceStories,
        MediaStories,
        DataStories,
        DateTimeStories,
        // A whole screen.
        ScreenStories,
    )
