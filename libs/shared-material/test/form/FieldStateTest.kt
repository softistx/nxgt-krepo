package com.strange.material.form

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun field(
    initial: String = "",
    vararg rules: Validation<String>,
) = FieldState("field", initial, rules.toList().all())

/**
 * The whole reason [FieldState] exists is the gap between *being* invalid and *saying so*. An empty
 * required field is invalid the instant the form is drawn, and a form that greets someone with six
 * complaints about work they have not started is the thing this type is here to prevent. So these
 * scenarios are about when the complaint appears, not about whether the rule works — `RulesTest`
 * has that.
 */
class FieldStateTest :
    FeatureSpec({
        feature("a field that is invalid from the start") {
            scenario("knows it, and says nothing") {
                val email = field(rules = arrayOf(Rules.required()))

                email.isValid shouldBe false
                email.error shouldBe "This field is required"
                email.showError shouldBe false
                email.visibleError.shouldBeNull()
            }

            scenario("speaks once the reader has left it") {
                val email = field(rules = arrayOf(Rules.required()))

                email.touch()

                email.showError shouldBe true
                email.visibleError shouldBe "This field is required"
            }

            scenario("speaks when a submit demands it, without having been visited") {
                val email = field(rules = arrayOf(Rules.required()))

                email.force()

                email.showError shouldBe true
            }

            scenario("stops speaking as soon as it is acceptable, having spoken once") {
                val email = field(rules = arrayOf(Rules.required()))
                email.touch()

                email.change("ada@example.com")

                email.isValid shouldBe true
                email.showError shouldBe false
            }
        }

        feature("a field's memory of what it was given") {
            scenario("is what dirty compares against, not merely 'has been typed in'") {
                val name = field("Ada")

                name.dirty shouldBe false
                name.change("Grace")
                name.dirty shouldBe true
                name.change("Ada")
                name.dirty shouldBe false
            }

            scenario("is what reset returns to, and it goes quiet again") {
                val name = field("Ada", Rules.required())
                name.change("")
                name.touch()

                name.reset()

                name.value shouldBe "Ada"
                name.touched shouldBe false
                name.forced shouldBe false
                name.showError shouldBe false
            }
        }
    })
