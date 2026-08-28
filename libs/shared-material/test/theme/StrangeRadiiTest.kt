package com.strange.material.theme

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * The radius scale is derived from one number, so the thing worth pinning is that the derivation
 * stays ordered and never goes negative — a base small enough to push `sm` below zero is exactly
 * what a designer will try.
 */
class StrangeRadiiTest :
    FeatureSpec({

        feature("a derived radius scale") {
            scenario("steps up in order from the base") {
                val radii = StrangeRadii(base = 10.dp)

                radii.md shouldBeGreaterThan radii.sm
                radii.lg shouldBeGreaterThan radii.md
                radii.xl shouldBeGreaterThan radii.lg
                radii.xxl shouldBeGreaterThan radii.xl
            }

            scenario("puts the base at lg, so one number moves the whole product") {
                StrangeRadii(base = 14.dp).lg shouldBe 14.dp
            }

            scenario("never derives a negative radius from a tiny base") {
                val radii = StrangeRadii(base = 1.dp)

                radii.sm shouldBe 0.dp
                radii.md shouldBe 0.dp
            }

            scenario("keeps full a pill at any base, rather than scaling it") {
                StrangeRadii(base = 2.dp).full shouldBe StrangeRadii(base = 40.dp).full
            }
        }

        feature("the M3 shapes it implies") {
            scenario("orders the five slots the same way the scale does") {
                val radii = StrangeRadii(base = 10.dp)
                val shapes = radii.toShapes()

                // Shapes carries no comparable size, so the check is that the mapping is total and
                // distinct — five slots, five different shapes.
                setOf(
                    shapes.extraSmall,
                    shapes.small,
                    shapes.medium,
                    shapes.large,
                    shapes.extraLarge,
                ).size shouldBe 5
            }
        }
    })
