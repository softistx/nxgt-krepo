package com.softistx.material.form.state

import io.konform.validation.Validation
import io.konform.validation.ValidationBuilder
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** The first thing a rule block complains about, which is the only message a field ever shows. */
private fun <T> complaint(
    value: T,
    rules: ValidationBuilder<T>.() -> Unit,
): String? = Validation(rules)(value).errors.firstOrNull()?.message

/**
 * The rules are the part of a form that is pure Kotlin, so they are the part that can be pinned
 * without a renderer. What is worth pinning is not that `required` rejects `""` — it is the two
 * decisions that are easy to reverse by accident: that a format rule lets a blank value through, so
 * an optional field with a format is one rule and not a special case, and that a block reports the
 * *first* complaint, which is what makes the message readable.
 */
class RulesTest :
    FeatureSpec({
        feature("a format rule") {
            scenario("passes a blank value, leaving the question of presence to required()") {
                complaint("") { email() }.shouldBeNull()
                complaint("") { minLength(8) }.shouldBeNull()
                complaint("") { digits() }.shouldBeNull()
            }

            scenario("still refuses a value that is present and wrong") {
                complaint("nobody") { email() } shouldBe "Enter a valid email address"
                complaint("short") { minLength(8) } shouldBe "Use at least 8 characters"
                complaint("12a") { digits() } shouldBe "Digits only"
            }

            scenario("accepts what it should") {
                complaint("ada@example.com") { email() }.shouldBeNull()
                complaint("abc") { minLength(3) }.shouldBeNull()
                complaint("abc") { maxLength(3) }.shouldBeNull()
                complaint("0042") { digits() }.shouldBeNull()
            }
        }

        feature("two rules in one block") {
            scenario("answer with the first complaint, which is why the order is the wording") {
                complaint("") {
                    required()
                    email()
                } shouldBe "This field is required"
                complaint("nobody") {
                    required()
                    email()
                } shouldBe "Enter a valid email address"
                complaint("ada@example.com") {
                    required()
                    email()
                }.shouldBeNull()
            }
        }

        feature("a message given at the call site") {
            scenario("replaces the English default, which is how a catalogue plugs in") {
                complaint("") { required("Ce champ est obligatoire") } shouldBe "Ce champ est obligatoire"
            }
        }

        feature("the rules for controls that are not text") {
            scenario("say what each control can get wrong") {
                complaint(false) { checked() } shouldBe "This has to be ticked"
                complaint(true) { checked() }.shouldBeNull()
                complaint<String?>(null) { chosen() } shouldBe "Choose one"
                complaint<String?>("a") { chosen() }.shouldBeNull()
                complaint(emptySet<String>()) { anyOf() } shouldBe "Choose at least one"
                complaint(setOf("a")) { anyOf() }.shouldBeNull()
                complaint(11f) { inRange(0f..10f) } shouldBe "Choose between 0.0 and 10.0"
                complaint(5f) { inRange(0f..10f) }.shouldBeNull()
            }
        }
    })
