package com.softistx.material.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * The catalogue must show the story, not only its knobs.
 *
 * `KnobsPanel` overlays a scrollbar with `fillMaxHeight`. In a wrap-content `Box` that modifier
 * used to take the pane's max height, so the panel ate the column and `StoryStage`'s `weight(1f)`
 * received nothing — controls visible, component gone. A marker colour in the story is the claim
 * the layout cannot fake: knobs never paint it.
 */
class CatalogPreviewTest :
    FeatureSpec({
        feature("the story stage") {
            scenario("keeps its height when the knobs panel is composed beside it") {
                val marker = Color(0xFF00E5A8)
                val groups =
                    listOf(
                        storyGroup("Probe") {
                            story("Marker") { _ ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(marker),
                                )
                            }
                        },
                    )
                val scene = ImageComposeScene(width = 1360, height = 900)
                try {
                    scene.setContent { MaterialDemo(groups = groups) }
                    var time = 0L
                    repeat(8) {
                        time += 100_000_000L
                        scene.render(time)
                    }
                    val map = scene.render(time).toComposeImageBitmap().toPixelMap()
                    var painted = 0
                    for (y in 0 until map.height) {
                        for (x in 0 until map.width) {
                            if (map[x, y] == marker) painted++
                        }
                    }
                    painted shouldBeGreaterThan 0
                } finally {
                    scene.close()
                }
            }
        }
    })
