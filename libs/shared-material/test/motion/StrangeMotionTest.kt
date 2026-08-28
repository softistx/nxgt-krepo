package com.strange.material.motion

import androidx.compose.animation.core.TweenSpec
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * The one behaviour here that a component silently depends on is [StrangeMotion.enabled]: every
 * animation in the library reads its spec from this object, so a false flag has to reach all of
 * them. A component that built its own `tween` would escape it, and this spec is what makes that
 * regression visible rather than merely wrong.
 */
class StrangeMotionTest :
    FeatureSpec({

        feature("the duration ladder") {
            scenario("increases from instant to slow") {
                val motion = StrangeMotion()

                motion.quick shouldBeGreaterThan motion.instant
                motion.standard shouldBeGreaterThan motion.quick
                motion.slow shouldBeGreaterThan motion.standard
            }

            scenario("keeps every duration inside what reads as responsive") {
                val motion = StrangeMotion()

                motion.instant shouldBeGreaterThan 0
                (motion.slow <= 500) shouldBe true
            }
        }

        feature("turning motion off") {
            scenario("collapses every spec to zero rather than merely shortening it") {
                val still = StrangeMotion(enabled = false)

                listOf(
                    still.spec<Float>(still.standard),
                    still.quickSpec<Float>(),
                    still.standardSpec<Float>(),
                    still.slowSpec<Float>(),
                ).forEach { spec ->
                    (spec as TweenSpec<Float>).durationMillis shouldBe 0
                }
            }

            scenario("leaves the named durations themselves untouched") {
                // The flag changes what a spec does, not what the tokens say — so turning motion
                // back on restores the same timings rather than a default set.
                StrangeMotion(enabled = false).standard shouldBe StrangeMotion().standard
            }
        }

        feature("specs built from the tokens") {
            scenario("carry the duration they were asked for when motion is on") {
                val motion = StrangeMotion()

                (motion.spec<Float>(motion.slow) as TweenSpec<Float>).durationMillis shouldBe
                    motion.slow
            }
        }
    })
