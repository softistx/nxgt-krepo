package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.button.Button
import com.strange.material.button.ButtonVariant
import com.strange.material.demo.storyGroup
import com.strange.material.form.Checkbox
import com.strange.material.form.CheckboxGroup
import com.strange.material.form.InputGroup
import com.strange.material.form.OtpField
import com.strange.material.form.RadioGroup
import com.strange.material.form.Rules
import com.strange.material.form.SelectField
import com.strange.material.form.SliderField
import com.strange.material.form.Switch
import com.strange.material.form.TextField
import com.strange.material.form.TextareaField
import com.strange.material.form.rememberField
import com.strange.material.icon.Icon
import com.strange.material.icon.StrangeIcons
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme

val FormStories =
    storyGroup("Forms") {
        story("Text field") { knobs ->
            val required = knobs.flag("Required", true)
            val secret = knobs.flag("Secret", false)
            val email = rememberField("", Rules.required(), Rules.email())
            Stack {
                TextField(
                    field = email,
                    label = "Email",
                    placeholder = "ada@example.com",
                    helper = if (required) "We only use it to sign you in" else null,
                    secret = secret,
                    leading = { Icon(icon = StrangeIcons.Person, description = null) },
                )
                Typography(text = "Leave the field to see the error appear — never before.")
            }
        }

        story("Textarea") { knobs ->
            val counted = knobs.flag("Character limit", true)
            val notes = rememberField("", Rules.maxLength(160))
            Stack {
                TextareaField(
                    field = notes,
                    label = "Notes",
                    placeholder = "Anything the courier should know",
                    maxLength = if (counted) 160 else null,
                )
            }
        }

        story("Select") { _ ->
            val country = rememberField<String?>(null, Rules.chosen())
            Stack {
                SelectField(
                    field = country,
                    options = listOf("France", "Ireland", "Japan", "Peru"),
                    label = "Country",
                )
            }
        }

        story("Checkbox and switch") { knobs ->
            val enabled = knobs.flag("Enabled", true)
            val terms = rememberField(false, Rules.checked())
            val digest = rememberField(true)
            Stack {
                Checkbox(
                    field = terms,
                    label = "I accept the terms",
                    helper = "You can withdraw at any time",
                    enabled = enabled,
                )
                Switch(
                    field = digest,
                    label = "Weekly digest",
                    description = "One message on Monday, nothing else",
                    enabled = enabled,
                )
            }
        }

        story("Choice groups") { _ ->
            val plan = rememberField<String?>(null, Rules.chosen())
            val channels = rememberField(emptySet<String>(), Rules.anyOf())
            Stack {
                RadioGroup(
                    field = plan,
                    options = listOf("Monthly", "Yearly"),
                    label = "Billing",
                    required = true,
                )
                CheckboxGroup(
                    field = channels,
                    options = listOf("Email", "SMS", "Push"),
                    label = "Tell me about deliveries by",
                )
            }
        }

        story("Slider") { _ ->
            val budget = rememberField(40f)
            Stack {
                SliderField(
                    field = budget,
                    label = "Monthly budget",
                    range = 0f..200f,
                    steps = 19,
                    format = { "€${it.toInt()}" },
                )
            }
        }

        story("One-time code") { knobs ->
            val length = knobs.number("Digits", 6f, 4f..8f, steps = 3).toInt()
            val code = rememberField("", Rules.minLength(length, "Enter all $length digits"))
            Stack {
                OtpField(field = code, length = length, label = "Code", helper = "Sent to your phone")
            }
        }

        story("Input group") { _ ->
            val voucher = rememberField("")
            Stack {
                InputGroup {
                    TextField(field = voucher, label = "Voucher", modifier = Modifier.weight(1f))
                    Button("Apply", onClick = {}, variant = ButtonVariant.Tonal)
                }
            }
        }
    }

/** Every story here is a column of fields; this is that column, once. */
@Composable
private fun Stack(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
    ) {
        content()
    }
}
