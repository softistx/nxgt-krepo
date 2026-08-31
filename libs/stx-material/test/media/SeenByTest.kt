package com.strange.material.media

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class SeenByTest :
    FeatureSpec({
        feature("seenByLabel") {
            scenario("nobody yet is sent, not seen by zero") {
                seenByLabel(0) shouldBe "Sent"
                seenByLabel(-1) shouldBe "Sent"
            }

            scenario("a count is seen by that many") {
                seenByLabel(1) shouldBe "Seen by 1"
                seenByLabel(12) shouldBe "Seen by 12"
            }
        }
    })
