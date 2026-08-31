package com.strange.material.display

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class FilterBarTest :
    FeatureSpec({
        feature("toggleFilter") {
            scenario("adds a name that was off") {
                toggleFilter(emptySet(), "Paid") shouldBe setOf("Paid")
            }

            scenario("removes a name that was on") {
                toggleFilter(setOf("Paid", "Pending"), "Paid") shouldBe setOf("Pending")
            }

            scenario("the empty set is everything, and toggling the last name returns to it") {
                toggleFilter(setOf("Paid"), "Paid") shouldBe emptySet()
            }
        }
    })
