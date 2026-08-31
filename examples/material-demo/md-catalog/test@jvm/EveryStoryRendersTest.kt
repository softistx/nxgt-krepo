package com.strange.material.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.strange.material.theme.StrangeTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Every story in the catalogue, rendered once.
 *
 * Two failures this catches, and both have happened here. A story that throws at display time
 * compiles perfectly and only shows itself when a reader clicks that one entry — the catalogue is
 * twenty-odd stories deep and nobody clicks them all. And a group that was written but never added
 * to [CatalogGroups] is invisible in exactly the same way as a group that does not exist.
 */
class EveryStoryRendersTest :
    FeatureSpec({
        val stories = CatalogGroups.flatMap(StoryGroup::stories)

        feature("the registry") {
            scenario("has every group the catalogue claims, each with stories in it") {
                CatalogGroups.map(StoryGroup::name) shouldBe
                    listOf(
                        "Foundation",
                        "Motion",
                        "Buttons",
                        "Display",
                        "Forms",
                        "Navigation",
                        "Surfaces",
                        "Date and time",
                        "Data",
                        "Media",
                        "Screens",
                    )
                CatalogGroups.forEach { it.stories.size shouldBeGreaterThan 0 }
            }

            scenario("gives every story its own id, so none can hide behind another") {
                stories.map(Story::id).distinct().size shouldBe stories.size
            }
        }

        feature("every story") {
            stories.forEach { story ->
                scenario("paints something: ${story.id}") {
                    ink(story) shouldBeGreaterThan 0
                }
            }
        }
    })

/** Pixels that are not the page, after the story has had time to settle. */
private fun ink(story: Story): Int {
    val scene = ImageComposeScene(width = 760, height = 900)
    try {
        scene.setContent {
            StrangeTheme(isDark = false) {
                val knobs = rememberKnobsStore()
                Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    StoryStage(story = story, knobs = knobs)
                }
            }
        }
        var time = 0L
        repeat(8) {
            time += 100_000_000L
            scene.render(time)
        }
        val map = scene.render(time).toComposeImageBitmap().toPixelMap()
        var ink = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                if (map[x, y] != Color.White) ink++
            }
        }
        return ink
    } finally {
        scene.close()
    }
}
