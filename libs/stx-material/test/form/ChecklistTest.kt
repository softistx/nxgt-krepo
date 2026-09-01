package com.softistx.material.form

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class ChecklistTest :
    FeatureSpec({
        val items =
            listOf(
                CheckItem("Pack", checked = true),
                CheckItem("Label"),
                CheckItem("Ship"),
            )

        feature("toggleCheckItem") {
            scenario("flips only the named row") {
                toggleCheckItem(items, 1).map { it.checked } shouldBe listOf(true, true, false)
            }

            scenario("an unknown index leaves the list as it was") {
                toggleCheckItem(items, 9) shouldBe items
            }
        }
    })
