package com.softistx.material.form.state

import io.konform.validation.Validation
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private data class SignUp(
    val email: String,
    val password: String,
    val confirm: String,
    val terms: Boolean,
)

private class SignUpForm :
    FormState<SignUp>(
        SignUp(email = "", password = "", confirm = "", terms = false),
        listOf(SignUp::email, SignUp::password, SignUp::confirm, SignUp::terms),
    ) {
    override val validate: Validation<SignUp> =
        Validation {
            SignUp::email {
                required()
                email()
            }
            SignUp::password {
                required()
                minLength(8)
            }
            dynamic(SignUp::confirm, { it.confirm }) { form -> matching(form.password) }
            SignUp::terms { checked() }
        }
}

/**
 * A form owns one value and asks its rules about the whole of it, so these scenarios are about the
 * gap between *being* invalid and *saying so*. An empty required form is invalid the instant it is
 * drawn, and a form that greets someone with four complaints about work they have not started is
 * the thing this type is here to prevent — so a complaint appears only for a path that has been
 * updated, or once a submit has demanded them all.
 */
class FormStateTest :
    FeatureSpec({
        feature("a form drawn for the first time") {
            scenario("is invalid, and says nothing") {
                val form = SignUpForm()

                form.isValid() shouldBe false
                form.errors.shouldBeEmpty()
                form.error(SignUp::email).shouldBeNull()
            }
        }

        feature("updating one path") {
            scenario("publishes that path's complaint and leaves the others silent") {
                val form = SignUpForm()

                form.update(SignUp::email) { it.copy(email = "nobody") }

                form.error(SignUp::email) shouldBe "Enter a valid email address"
                form.hasError(SignUp::password) shouldBe false
            }

            scenario("takes the complaint away as soon as the value is acceptable") {
                val form = SignUpForm()
                form.update(SignUp::email) { it.copy(email = "nobody") }

                form.update(SignUp::email) { it.copy(email = "ada@example.com") }

                form.hasError(SignUp::email) shouldBe false
                form.value.email shouldBe "ada@example.com"
            }
        }

        feature("a rule that reads another field") {
            scenario("compares against the value as it stands, not as it was when the rules were built") {
                val form = SignUpForm()
                form.update(SignUp::password) { it.copy(password = "correct horse") }

                form.update(SignUp::confirm) { it.copy(confirm = "correct horse") }
                form.hasError(SignUp::confirm) shouldBe false

                form.update(SignUp::password) { it.copy(password = "battery staple") }
                form.update(SignUp::confirm) { it.copy(confirm = "correct horse") }
                form.error(SignUp::confirm) shouldBe "The two do not match"
            }
        }

        feature("submitting") {
            scenario("makes every field speak at once, not one at a time") {
                val form = SignUpForm()

                form.validateAll() shouldBe false

                form.hasError(SignUp::email) shouldBe true
                form.hasError(SignUp::password) shouldBe true
                form.hasError(SignUp::terms) shouldBe true
            }

            scenario("runs the block with the value only when there is nothing left to complain about") {
                val form = SignUpForm()
                var sent: SignUp? = null

                form.submit { sent = it }
                sent.shouldBeNull()

                form.update(SignUp::email) { it.copy(email = "ada@example.com") }
                form.update(SignUp::password) { it.copy(password = "correct horse") }
                form.update(SignUp::confirm) { it.copy(confirm = "correct horse") }
                form.update(SignUp::terms) { it.copy(terms = true) }

                form.submit { sent = it }
                sent?.email shouldBe "ada@example.com"
            }
        }

        feature("what the form remembers it was given") {
            scenario("is what dirty compares against, not merely 'has been typed in'") {
                val form = SignUpForm()

                form.isDirty() shouldBe false
                form.update(SignUp::email) { it.copy(email = "ada@example.com") }
                form.isDirty() shouldBe true
                form.update(SignUp::email) { it.copy(email = "") }
                form.isDirty() shouldBe false
            }

            scenario("is what reset returns to, and it goes quiet again") {
                val form = SignUpForm()
                form.update(SignUp::email) { it.copy(email = "nobody") }
                form.validateAll()

                form.reset()

                form.value.email shouldBe ""
                form.errors.shouldBeEmpty()
            }
        }

        feature("clearing the errors") {
            scenario("puts every complaint away without touching a value") {
                val form = SignUpForm()
                form.update(SignUp::email) { it.copy(email = "nobody") }

                form.clearErrors()

                form.errors.shouldBeEmpty()
                form.value.email shouldBe "nobody"
            }
        }
    })
