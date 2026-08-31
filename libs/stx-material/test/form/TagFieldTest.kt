package com.strange.material.form

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class TagFieldTest :
    FeatureSpec({
        feature("addTag") {
            scenario("adds a trimmed name") {
                addTag(emptyList(), "  urgent  ") shouldBe listOf("urgent")
            }

            scenario("ignores a blank") {
                addTag(listOf("urgent"), "   ") shouldBe listOf("urgent")
            }

            scenario("ignores a duplicate, case insensitive") {
                addTag(listOf("Urgent"), "urgent") shouldBe listOf("Urgent")
            }
        }

        feature("removeTag") {
            scenario("drops the matching name, ignoring case") {
                removeTag(listOf("urgent", "europe"), "URGENT") shouldBe listOf("europe")
            }
        }
    })
