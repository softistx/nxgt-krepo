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
import com.strange.material.form.Rules
import com.strange.material.form.SelectField
import com.strange.material.form.TextField
import com.strange.material.form.field
import com.strange.material.form.rememberForm
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/**
 * The acceptance criterion for the forms phase, not a demonstration.
 *
 * Eight controls, cross-field validation, errors that wait their turn, a submit button that knows
 * whether it may be pressed, and a success banner — and the only `remember` in the file is
 * `submitted`, which is business state a real screen would own too. No `onValueChange`, no error
 * booleans, no `touched` flags, no `animate*AsState`.
 *
 * If a later phase makes this file need plumbing, the phase is not finished.
 */
@Composable
fun SignUpScreen(modifier: Modifier = Modifier) {
    val form = rememberForm()
    val name = form.field("name", "", Rules.required())
    val email = form.field("email", "", Rules.required(), Rules.email())
    val password = form.field("password", "", Rules.required(), Rules.minLength(8))
    val confirm = form.field("confirm", "", Rules.matching({ password.value }))
    val country = form.field<String?>("country", null, Rules.chosen())
    val billing = form.field<String?>("billing", "Monthly", Rules.chosen())
    val terms = form.field("terms", false, Rules.checked())
    var submitted by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
    ) {
        Typography(text = "Create an account", variant = TypographyVariant.HeadlineSmall)

        Alert(
            text = "Welcome, ${name.value}. Check your email to confirm.",
            tone = Tone.Success,
            visible = submitted,
        )

        TextField(field = name, label = "Full name")
        TextField(field = email, label = "Email", placeholder = "ada@example.com")
        TextField(field = password, label = "Password", secret = true, helper = "At least 8 characters")
        TextField(field = confirm, label = "Confirm password", secret = true)
        SelectField(
            field = country,
            options = listOf("France", "Ireland", "Japan", "Peru"),
            label = "Country",
        )
        RadioGroup(field = billing, options = listOf("Monthly", "Yearly"), label = "Billing")
        Checkbox(field = terms, label = "I accept the terms")

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
