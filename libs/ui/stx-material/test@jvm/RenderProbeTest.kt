package com.softistx.material

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe

class RenderProbeTest :
    FeatureSpec({
        feature("reading a pixel at a node's corner") {
            scenario("a circle leaves its corner transparent and fills its centre") {
                val box = NodeBounds()
                renderProbe(content = { Swatch(CircleShape, box) }) {
                    val frame = settle()
                    frame.isTransparent(box, Corner.TopLeft) shouldBe true
                    frame.isFilled(box, Corner.Center) shouldBe true
                }
            }

            scenario("a rectangle fills its corner, and an inset point of a circle is filled too") {
                val rect = NodeBounds()
                val circle = NodeBounds()
                renderProbe(content = {
                    Swatch(RectangleShape, rect)
                    Swatch(CircleShape, circle, Modifier.offset(y = 100.dp))
                }) {
                    val frame = settle()
                    frame.isFilled(rect, Corner.TopLeft) shouldBe true
                    frame.isTransparent(circle, Corner.BottomRight) shouldBe true
                    frame.isFilled(circle, Corner.BottomRight, inset = 14) shouldBe true
                }
            }
        }

        feature("counting ink") {
            scenario("a rectangle inks its whole bounds and a circle fewer pixels") {
                val rect = NodeBounds()
                val circle = NodeBounds()
                renderProbe(content = {
                    Swatch(RectangleShape, rect)
                    Swatch(CircleShape, circle, Modifier.offset(y = 100.dp))
                }) {
                    val frame = settle()
                    frame.ink(rect) shouldBe SIZE * SIZE
                    frame.ink(circle) shouldBeGreaterThan 0
                    frame.ink(circle) shouldBeLessThan SIZE * SIZE
                }
            }
        }

        feature("pressing a node") {
            scenario("a pressed node renders differently from the same node at rest") {
                val node = NodeBounds()
                renderProbe(content = { PressSwatch(node) }) {
                    val rest = settle()
                    val pressed = press(node)
                    rest.pixelAt(node, Corner.Center) shouldBe Color.Black
                    pressed.pixelAt(node, Corner.Center) shouldBe Color.Red
                }
            }
        }
    })

private const val SIZE = 80

@Composable
private fun Swatch(
    shape: Shape,
    into: NodeBounds,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(SIZE.dp).probed(into).background(Color.Black, shape))
}

/** Black at rest, red from the moment a pointer is pressed on it. */
@Composable
private fun PressSwatch(into: NodeBounds) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(SIZE.dp)
            .probed(into)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true }) }
            .background(if (pressed) Color.Red else Color.Black),
    )
}
