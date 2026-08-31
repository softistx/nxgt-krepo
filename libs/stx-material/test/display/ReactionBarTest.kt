package com.strange.material.display

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class ReactionBarTest :
    FeatureSpec({
        feature("toggleReaction") {
            scenario("selecting adds one") {
                toggleReaction(3, selected = false) shouldBe (4 to true)
            }

            scenario("deselecting removes one and never goes below zero") {
                toggleReaction(1, selected = true) shouldBe (0 to false)
                toggleReaction(0, selected = true) shouldBe (0 to false)
            }
        }
    })
