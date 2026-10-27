package com.softistx.material.foundation.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeGreaterThan as inkShouldBeGreaterThan

private const val STEP_MILLIS = 100

/** How dark the item's centre is: 0 while it is invisible, 1 once it has fully arrived. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.darkness(): Float {
    val map = onRoot().captureToImage().toPixelMap()
    return 1f - map[20, 20].red
}

/** The item's arrival is on the frame clock, so a test or a headless render can step through it. */
@OptIn(ExperimentalTestApi::class)
class StaggerTest :
    FeatureSpec({
        feature("animateStagger") {
            scenario("timing is unchanged on the test clock: it waits, arrives mid-animation, then settles") {
                runComposeUiTest {
                    mainClock.autoAdvance = false
                    setContent {
                        StxTheme(isDark = false) {
                            Box(Modifier.fillMaxSize().background(Color.White)) { item() }
                        }
                    }
                    mainClock.advanceTimeBy(300)
                    darkness() shouldBe 0f
                    mainClock.advanceTimeBy(150)
                    darkness() shouldBeGreaterThan 0f
                    darkness() shouldBeLessThan 1f
                    mainClock.advanceTimeBy(2_000)
                    darkness() shouldBe 1f
                }
            }
            scenario("a headless render, which has no wall clock, sees the item arrive") {
                renderProbe(content = { item() }) {
                    firstFrame().ink() shouldBe 0
                    // 200 ms of a 400 ms wait: still invisible, so the wait is counted in frames, not skipped.
                    advance(2).ink() shouldBe 0
                    settle().ink() inkShouldBeGreaterThan 0
                }
            }
            scenario("with reduced motion the item is shown at once") {
                renderProbe(
                    content = { StxTheme(isDark = false, motion = StxMotion(enabled = false)) { item() } },
                ) {
                    firstFrame().ink() inkShouldBeGreaterThan 0
                }
            }
        }
    })

@Composable
private fun item() {
    Box(
        Modifier
            .size(40.dp)
            .animateStagger(index = 4, stepMillis = STEP_MILLIS)
            .background(Color.Black),
    )
}
