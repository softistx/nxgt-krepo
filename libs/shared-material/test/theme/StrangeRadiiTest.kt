package com.strange.material.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The radii are Material 3's own ladder, shifted so `medium` lands on `base`. What is worth
 * pinning is the shifting: that the order survives it, that a tiny base flattens rather than
 * inverts, and that all eight slots move together — three of them were added in M3 expressive and
 * are exactly the ones a hand-written `Shapes(…)` leaves behind.
 */
class StrangeRadiiTest :
    FeatureSpec({
        feature("the ladder") {
            scenario("rises through all eight of Material 3's slots") {
                val radii = StrangeRadii()

                radii.small shouldBeGreaterThan radii.extraSmall
                radii.medium shouldBeGreaterThan radii.small
                radii.large shouldBeGreaterThan radii.medium
                radii.largeIncreased shouldBeGreaterThan radii.large
                radii.extraLarge shouldBeGreaterThan radii.largeIncreased
                radii.extraLargeIncreased shouldBeGreaterThan radii.extraLarge
                radii.extraExtraLarge shouldBeGreaterThan radii.extraLargeIncreased
            }

            scenario("is Material 3's own at the default base, so the default theme is not a redesign") {
                val radii = StrangeRadii()

                radii.extraSmall shouldBe 4.dp
                radii.small shouldBe 8.dp
                radii.medium shouldBe 12.dp
                radii.large shouldBe 16.dp
                radii.extraLarge shouldBe 28.dp
                radii.extraExtraLarge shouldBe 48.dp
            }

            scenario("puts the base on medium, so one number moves the whole product") {
                StrangeRadii(base = 20.dp).medium shouldBe 20.dp
                StrangeRadii(base = 20.dp).large shouldBe 24.dp
            }

            scenario("flattens rather than inverting when the base is smaller than the shift") {
                val sharp = StrangeRadii(base = 0.dp)

                sharp.extraSmall shouldBe 0.dp
                sharp.small shouldBe 0.dp
                sharp.medium shouldBe 0.dp
                sharp.large shouldBe 4.dp
            }
        }

        feature("the Shapes it hands Material 3") {
            scenario("fills all eight slots, not the five a Shapes(…) call defaults") {
                val radii = StrangeRadii(base = 20.dp)
                val shapes = radii.toShapes()

                shapes.extraSmall shouldBe RoundedCornerShape(radii.extraSmall)
                shapes.small shouldBe RoundedCornerShape(radii.small)
                shapes.medium shouldBe RoundedCornerShape(radii.medium)
                shapes.large shouldBe RoundedCornerShape(radii.large)
                shapes.largeIncreased shouldBe RoundedCornerShape(radii.largeIncreased)
                shapes.extraLarge shouldBe RoundedCornerShape(radii.extraLarge)
                shapes.extraLargeIncreased shouldBe RoundedCornerShape(radii.extraLargeIncreased)
                shapes.extraExtraLarge shouldBe RoundedCornerShape(radii.extraExtraLarge)
            }

            scenario("moves every slot when the base moves — none is left on the M3 default") {
                val moved = StrangeRadii(base = 20.dp).toShapes()
                val default = StrangeRadii().toShapes()

                moved.largeIncreased shouldNotBe default.largeIncreased
                moved.extraLargeIncreased shouldNotBe default.extraLargeIncreased
                moved.extraExtraLarge shouldNotBe default.extraExtraLarge
            }
        }
    })
