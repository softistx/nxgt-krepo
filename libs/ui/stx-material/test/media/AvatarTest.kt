package com.softistx.material.media

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class AvatarTest :
    FeatureSpec({
        feature("initials") {
            scenario("takes the first letters of the first and last words") {
                initials("Amara Diallo") shouldBe "AD"
            }

            scenario("a single word takes the first two letters") {
                initials("Amara") shouldBe "AM"
            }

            scenario("blank is empty, not a crash") {
                initials("   ") shouldBe ""
            }
        }
    })
