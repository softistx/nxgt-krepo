package com.softistx.material.demo

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * The story list is taller than any window as soon as there are a few groups, so it has to scroll —
 * and a pane that does not scroll hides whatever was added last, silently and without an error.
 *
 * Rendered headlessly and scrolled with a real wheel event, because there is no other way to be
 * sure: the layout reads as correct either way.
 */
class StoryListScrollTest :
    FeatureSpec({
        feature("the left pane") {
            scenario("moves under the wheel, so the last group added is reachable") {
                val scene = ImageComposeScene(width = 1200, height = 700)
                try {
                    scene.setContent { MaterialDemo() }

                    fun column(): List<Color> {
                        var time = 0L
                        repeat(8) {
                            time += 100_000_000L
                            scene.render(time)
                        }
                        val map = scene.render(time).toComposeImageBitmap().toPixelMap()
                        return (120 until 690).map { map[130, it] }
                    }

                    val before = column()
                    scene.sendPointerEvent(PointerEventType.Enter, Offset(130f, 400f))
                    scene.sendPointerEvent(PointerEventType.Move, Offset(131f, 400f))
                    repeat(6) {
                        scene.sendPointerEvent(
                            eventType = PointerEventType.Scroll,
                            position = Offset(131f, 400f),
                            scrollDelta = Offset(0f, 4f),
                        )
                    }
                    val after = column()

                    (after == before) shouldBe false
                } finally {
                    scene.close()
                }
            }
        }
    })
