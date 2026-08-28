package com.strange.material.form

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private fun FormState.text(
    name: String,
    initial: String = "",
    vararg rules: Validation<String>,
) = register(name) { FieldState(name, initial, rules.toList().all()) }

/**
 * A form holds no copy of anything — it asks its fields. These scenarios are what that claim is
 * worth: that validity follows the fields as they change rather than being recomputed by hand, that
 * a submit shows every complaint at once rather than the first, and that declaring the same field
 * twice answers the same field rather than quietly making a second one that nothing reads.
 */
class FormStateTest :
    FeatureSpec({
        feature("a form's validity") {
            scenario("follows its fields, with nothing to keep in step") {
                val form = FormState()
                val email = form.text("email", rules = arrayOf(Rules.required()))
                form.text("name", "Ada")

                form.isValid shouldBe false
                email.change("ada@example.com")
                form.isValid shouldBe true
            }
        }

        feature("submitting") {
            scenario("makes every field speak at once, not one at a time") {
                val form = FormState()
                val email = form.text("email", rules = arrayOf(Rules.required()))
                val name = form.text("name", rules = arrayOf(Rules.required()))

                form.validate() shouldBe false

                email.showError shouldBe true
                name.showError shouldBe true
                form.submitted shouldBe true
            }

            scenario("runs the block only when there is nothing left to complain about") {
                val form = FormState()
                val email = form.text("email", rules = arrayOf(Rules.required()))
                var sent = 0

                form.submit { sent++ }
                sent shouldBe 0

                email.change("ada@example.com")
                form.submit { sent++ }
                sent shouldBe 1
            }

            scenario("hands over the values under the names they were declared with") {
                val form = FormState()
                form.text("email", "ada@example.com")
                form.register("terms") { FieldState("terms", true, emptyList<Validation<Boolean>>().all()) }

                form.values() shouldBe mapOf("email" to "ada@example.com", "terms" to true)
            }
        }

        feature("declaring the same field twice") {
            scenario("answers the field that already exists, so a recomposition cannot lose a value") {
                val form = FormState()
                val first = form.text("email")
                first.change("ada@example.com")

                val second = form.text("email")

                (second === first) shouldBe true
                second.value shouldBe "ada@example.com"
            }
        }

        feature("resetting") {
            scenario("puts every field back and makes the form quiet again") {
                val form = FormState()
                val email = form.text("email", rules = arrayOf(Rules.required()))
                form.validate()

                form.reset()

                form.submitted shouldBe false
                email.showError shouldBe false
            }
        }
    })
