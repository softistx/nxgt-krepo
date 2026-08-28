package com.strange.material.form

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * The rules are the part of a form that is pure Kotlin, so they are the part that can be pinned
 * without a renderer. What is worth pinning is not that `required` rejects `""` — it is the two
 * decisions that are easy to reverse by accident: that a format rule lets a blank value through, so
 * an optional field with a format is one rule and not a special case, and that `and` reports the
 * *first* complaint, which is what makes the message readable.
 */
class RulesTest :
    FeatureSpec({
        feature("a format rule") {
            scenario("passes a blank value, leaving the question of presence to required()") {
                Rules.email().check("").shouldBeNull()
                Rules.minLength(8).check("").shouldBeNull()
                Rules.digits().check("").shouldBeNull()
            }

            scenario("still refuses a value that is present and wrong") {
                Rules.email().check("nobody") shouldBe "Enter a valid email address"
                Rules.minLength(8).check("short") shouldBe "Use at least 8 characters"
                Rules.digits().check("12a") shouldBe "Digits only"
            }

            scenario("accepts what it should") {
                Rules.email().check("ada@example.com").shouldBeNull()
                Rules.minLength(3).check("abc").shouldBeNull()
                Rules.maxLength(3).check("abc").shouldBeNull()
                Rules.digits().check("0042").shouldBeNull()
            }
        }

        feature("two rules combined") {
            scenario("answer with the first complaint, which is why the order is the wording") {
                val rule = Rules.required() and Rules.email()

                rule.check("") shouldBe "This field is required"
                rule.check("nobody") shouldBe "Enter a valid email address"
                rule.check("ada@example.com").shouldBeNull()
            }

            scenario("reversed, they say something true and useless about an empty box") {
                (Rules.email() and Rules.required()).check("") shouldBe "This field is required"
            }
        }

        feature("a rule that compares against another field") {
            scenario("reads it at the moment it checks, not when it was built") {
                var password = "first"
                val rule = Rules.matching({ password })

                rule.check("first").shouldBeNull()
                password = "second"
                rule.check("first") shouldBe "The two do not match"
                rule.check("second").shouldBeNull()
            }
        }

        feature("the rules for controls that are not text") {
            scenario("say what each control can get wrong") {
                Rules.checked().check(false) shouldBe "This has to be ticked"
                Rules.checked().check(true).shouldBeNull()
                Rules.chosen<String>().check(null) shouldBe "Choose one"
                Rules.chosen<String>().check("a").shouldBeNull()
                Rules.anyOf<String>().check(emptySet()) shouldBe "Choose at least one"
                Rules.anyOf<String>().check(setOf("a")).shouldBeNull()
                Rules.inRange(0f..10f).check(11f) shouldBe "Choose between 0.0 and 10.0"
            }
        }
    })
