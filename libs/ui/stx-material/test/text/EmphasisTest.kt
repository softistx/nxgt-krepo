package com.softistx.material.text

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

/**
 * Emphasis is the one part of the type layer with a number in it, and the numbers carry a promise:
 * three visibly distinct levels, none of them invisible. A well-meaning tweak that made `Subtle`
 * 0.2 would pass review and fail a reader.
 */
class EmphasisTest :
    FeatureSpec({
        feature("the emphasis levels") {
            scenario("run from full to subtle without repeating a value") {
                Emphasis.entries.map { it.alpha } shouldBe Emphasis.entries.map { it.alpha }.sortedDescending()
                Emphasis.entries
                    .map { it.alpha }
                    .toSet()
                    .size shouldBe Emphasis.entries.size
            }

            scenario("stay readable — nothing is transparent, nothing exceeds opaque") {
                Emphasis.entries.forEach { level ->
                    level.alpha shouldBeGreaterThan 0.4f
                    level.alpha shouldBeLessThanOrEqual 1f
                }
            }

            scenario("start at fully opaque, because that is the default a caller gets") {
                Emphasis.Full.alpha shouldBe 1f
            }
        }
    })
