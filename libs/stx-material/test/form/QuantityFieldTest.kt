package com.strange.material.form

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class QuantityFieldTest :
    FeatureSpec({
        feature("stepQuantity") {
            scenario("steps up inside the range") {
                stepQuantity(2, 1, 0..12) shouldBe 3
            }

            scenario("does not leave the range") {
                stepQuantity(12, 1, 0..12) shouldBe 12
                stepQuantity(0, -1, 0..12) shouldBe 0
            }
        }
    })
