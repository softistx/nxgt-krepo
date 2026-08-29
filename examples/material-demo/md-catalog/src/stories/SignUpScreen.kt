package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.strange.material.button.Button
import com.strange.material.button.ButtonRow
import com.strange.material.button.ButtonVariant
import com.strange.material.display.Alert
import com.strange.material.form.Checkbox
import com.strange.material.form.RadioGroup
import com.strange.material.form.SelectField
import com.strange.material.form.TextField
import com.strange.material.form.state.FormState
import com.strange.material.form.state.checked
import com.strange.material.form.state.chosen
import com.strange.material.form.state.email
import com.strange.material.form.state.matching
import com.strange.material.form.state.minLength
import com.strange.material.form.state.rememberForm
import com.strange.material.form.state.required
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone
import io.konform.validation.Validation

/** What the screen is assembling. A form owns one of these; every field is a property of it. */
private data class SignUpInput(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirm: String = "",
    val country: String? = null,
    val billing: String? = "Monthly",
    val terms: Boolean = false,
)

/**
 * Every rule the screen has, in one block.
 *
 * `dynamic` is how a rule reads the rest of the form: the confirmation is checked against the
 * password *as it stands*, not as it was when the rules were built.
 */
private class SignUpForm :
    FormState<SignUpInput>(
        SignUpInput(),
        listOf(
            SignUpInput::name,
            SignUpInput::email,
            SignUpInput::password,
            SignUpInput::confirm,
            SignUpInput::country,
            SignUpInput::billing,
            SignUpInput::terms,
        ),
    ) {
    override val validate: Validation<SignUpInput> =
        Validation {
            SignUpInput::name { required() }
            SignUpInput::email {
                required()
                email()
            }
            SignUpInput::password {
                required()
                minLength(8)
            }
            dynamic(SignUpInput::confirm, { it.confirm }) { form -> matching(form.password) }
            SignUpInput::country { chosen() }
            SignUpInput::billing { chosen() }
            SignUpInput::terms { checked() }
        }
}

/**
 * The acceptance criterion for the forms phase, not a demonstration.
 *
 * Seven controls, cross-field validation, errors that wait their turn, and a success banner — with
 * one value behind all of it. `form.value` is the request body, so submitting is `send(form.value)`
 * and never a hand-assembled map, and no control here owns any state of its own.
 *
 * A complaint appears only for a property that has been typed in, or once *Create account* has
 * demanded them all. That is why an empty form is silent and still knows it is invalid.
 */
@Composable
fun SignUpScreen(modifier: Modifier = Modifier) {
    val form = rememberForm { SignUpForm() }
    var submitted by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
    ) {
        Typography(text = "Create an account", variant = TypographyVariant.HeadlineSmall)

        Alert(
            text = "Welcome, ${form.value.name}. Check your email to confirm.",
            tone = Tone.Success,
            visible = submitted,
        )

        TextField(
            value = form.value.name,
            onValueChange = { next -> form.update(SignUpInput::name) { it.copy(name = next) } },
            label = "Full name",
            isError = form.hasError(SignUpInput::name),
            supportingText = form.error(SignUpInput::name),
        )
        TextField(
            value = form.value.email,
            onValueChange = { next -> form.update(SignUpInput::email) { it.copy(email = next) } },
            label = "Email",
            placeholder = "ada@example.com",
            isError = form.hasError(SignUpInput::email),
            supportingText = form.error(SignUpInput::email),
        )
        TextField(
            value = form.value.password,
            onValueChange = { next ->
                form.update(SignUpInput::password) { it.copy(password = next) }
            },
            label = "Password",
            secret = true,
            helper = "At least 8 characters",
            isError = form.hasError(SignUpInput::password),
            supportingText = form.error(SignUpInput::password),
        )
        TextField(
            value = form.value.confirm,
            onValueChange = { next ->
                form.update(SignUpInput::confirm) { it.copy(confirm = next) }
            },
            label = "Confirm password",
            secret = true,
            isError = form.hasError(SignUpInput::confirm),
            supportingText = form.error(SignUpInput::confirm),
        )
        SelectField(
            value = form.value.country,
            onValueChange = { next ->
                form.update(SignUpInput::country) { it.copy(country = next) }
            },
            options = listOf("France", "Ireland", "Japan", "Peru"),
            label = "Country",
            isError = form.hasError(SignUpInput::country),
            supportingText = form.error(SignUpInput::country),
        )
        RadioGroup(
            value = form.value.billing,
            onValueChange = { next ->
                form.update(SignUpInput::billing) { it.copy(billing = next) }
            },
            options = listOf("Monthly", "Yearly"),
            label = "Billing",
            supportingText = form.error(SignUpInput::billing),
        )
        Checkbox(
            value = form.value.terms,
            onValueChange = { next -> form.update(SignUpInput::terms) { it.copy(terms = next) } },
            label = "I accept the terms",
            supportingText = form.error(SignUpInput::terms),
        )

        ButtonRow {
            Button(
                text = "Reset",
                onClick = {
                    form.reset()
                    submitted = false
                },
                variant = ButtonVariant.Ghost,
            )
            Button(text = "Create account", onClick = { form.submit { submitted = true } })
        }
    }
}
