package com.softistx.material.motion

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.material3.MotionScheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf

/**
 * The curves themselves belong to Material 3 and are not this library's to assert. What is ours is
 * the wiring: that the two axes stay distinct, that the three speeds stay distinct, that turning
 * motion off really stops the clock, and that two default themes compare equal.
 */
class StxMotionTest :
    FeatureSpec({
        feature("the two axes Material 3 separates") {
            scenario("do not collapse into one — a fade and a slide are not the same curve") {
                val motion = StxMotion()

                motion.spatial<Float>() shouldNotBe motion.effects<Float>()
            }

            scenario("each answer differently at each of the three speeds") {
                val motion = StxMotion()

                setOf(
                    motion.spatial<Float>(MotionSpeed.Fast),
                    motion.spatial<Float>(MotionSpeed.Default),
                    motion.spatial<Float>(MotionSpeed.Slow),
                ).size shouldBe 3
                setOf(
                    motion.effects<Float>(MotionSpeed.Fast),
                    motion.effects<Float>(MotionSpeed.Default),
                    motion.effects<Float>(MotionSpeed.Slow),
                ).size shouldBe 3
            }
        }

        feature("the shape of the two axes") {
            scenario("spatial overshoots — so it may only drive a value that tolerates leaving its range") {
                val motion = StxMotion()

                MotionSpeed.entries.forEach { speed ->
                    motion
                        .spatial<Float>(speed)
                        .shouldBeInstanceOf<SpringSpec<Float>>()
                        .dampingRatio shouldBeLessThan 1f
                }
            }

            scenario("effects does not — so colour and alpha land exactly where they were sent") {
                val motion = StxMotion()

                MotionSpeed.entries.forEach { speed ->
                    motion
                        .effects<Float>(speed)
                        .shouldBeInstanceOf<SpringSpec<Float>>()
                        .dampingRatio shouldBeGreaterThanOrEqualTo 1f
                }
            }
        }

        feature("turning motion off") {
            scenario("collapses every spec to a snap rather than merely shortening it") {
                val still = StxMotion(enabled = false)

                MotionSpeed.entries.forEach { speed ->
                    still.spatial<Float>(speed).shouldBeInstanceOf<SnapSpec<Float>>()
                    still.effects<Float>(speed).shouldBeInstanceOf<SnapSpec<Float>>()
                }
            }

            scenario("leaves the scheme itself untouched, so turning it back on restores the curves") {
                val still = StxMotion(enabled = false)

                still.scheme shouldBe StxMotion().scheme
                still.copy(enabled = true).spatial<Float>() shouldBe StxMotion().spatial<Float>()
            }
        }

        feature("motion that is on") {
            scenario("never answers with a snap") {
                val motion = StxMotion()

                motion.spatial<Float>().shouldNotBeInstanceOf<SnapSpec<Float>>()
                motion.effects<Float>().shouldNotBeInstanceOf<SnapSpec<Float>>()
            }
        }

        feature("the scheme it is given") {
            scenario("actually drives the curves — standard and expressive are not the same") {
                val expressive = StxMotion(MotionScheme.expressive())
                val standard = StxMotion(MotionScheme.standard())

                expressive.spatial<Float>() shouldNotBe standard.spatial<Float>()
            }

            scenario("makes two default themes compare equal, so installing one is not a recomposition") {
                StxMotion() shouldBe StxMotion()
            }
        }
    })
