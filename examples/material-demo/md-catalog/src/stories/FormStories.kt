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
import com.strange.material.button.ButtonVariant
import com.strange.material.demo.storyGroup
import com.strange.material.form.Checkbox
import com.strange.material.form.CheckboxGroup
import com.strange.material.form.InputGroup
import com.strange.material.form.OtpField
import com.strange.material.form.RadioGroup
import com.strange.material.form.SelectField
import com.strange.material.form.SliderField
import com.strange.material.form.Switch
import com.strange.material.form.TextField
import com.strange.material.form.TextareaField
import com.strange.material.icon.Icon
import com.strange.material.icon.StrangeIcons
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme

/**
 * The controls on their own.
 *
 * Nothing here builds a form, and that is the point of the split: a control takes a value, a way to
 * change it and — where it can be wrong — a complaint to show, so it works just as well against a
 * `var x by remember` as against a `FormState`. The *Complaint* knob is what a form would be
 * passing in; the whole pattern in one piece is the **Sign up** screen story.
 */
val FormStories =
    storyGroup("Forms") {
        story("Text field") { knobs ->
            val complaining = knobs.flag("Complaint", false)
            val secret = knobs.flag("Secret", false)
            var email by remember { mutableStateOf("") }
            Stack {
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    placeholder = "ada@example.com",
                    helper = "We only use it to sign you in",
                    isError = complaining,
                    supportingText = "Enter a valid email address".takeIf { complaining },
                    secret = secret,
                    leading = { Icon(icon = StrangeIcons.Person, description = null) },
                )
                Typography(text = "A complaint takes the hint's place rather than sitting beside it.")
            }
        }

        story("Textarea") { knobs ->
            val counted = knobs.flag("Character limit", true)
            var notes by remember { mutableStateOf("") }
            Stack {
                TextareaField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes",
                    placeholder = "Anything the courier should know",
                    maxLength = if (counted) 160 else null,
                )
            }
        }

        story("Select") { knobs ->
            val complaining = knobs.flag("Complaint", false)
            var country by remember { mutableStateOf<String?>(null) }
            Stack {
                SelectField(
                    value = country,
                    onValueChange = { country = it },
                    options = listOf("France", "Ireland", "Japan", "Peru"),
                    label = "Country",
                    isError = complaining,
                    supportingText = "Choose one".takeIf { complaining },
                )
            }
        }

        story("Checkbox and switch") { knobs ->
            val enabled = knobs.flag("Enabled", true)
            val complaining = knobs.flag("Complaint", false)
            var terms by remember { mutableStateOf(false) }
            var digest by remember { mutableStateOf(true) }
            Stack {
                Checkbox(
                    value = terms,
                    onValueChange = { terms = it },
                    label = "I accept the terms",
                    helper = "You can withdraw at any time",
                    supportingText = "This has to be ticked".takeIf { complaining },
                    enabled = enabled,
                )
                Switch(
                    value = digest,
                    onValueChange = { digest = it },
                    label = "Weekly digest",
                    description = "One message on Monday, nothing else",
                    enabled = enabled,
                )
            }
        }

        story("Choice groups") { knobs ->
            val complaining = knobs.flag("Complaint", false)
            var plan by remember { mutableStateOf<String?>(null) }
            var channels by remember { mutableStateOf(emptySet<String>()) }
            Stack {
                RadioGroup(
                    value = plan,
                    onValueChange = { plan = it },
                    options = listOf("Monthly", "Yearly"),
                    label = "Billing",
                    required = true,
                    supportingText = "Choose one".takeIf { complaining },
                )
                CheckboxGroup(
                    value = channels,
                    onValueChange = { channels = it },
                    options = listOf("Email", "SMS", "Push"),
                    label = "Tell me about deliveries by",
                    supportingText = "Choose at least one".takeIf { complaining },
                )
            }
        }

        story("Slider") { _ ->
            var budget by remember { mutableStateOf(40f) }
            Stack {
                SliderField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = "Monthly budget",
                    range = 0f..200f,
                    steps = 19,
                    format = { "€${it.toInt()}" },
                )
            }
        }

        story("One-time code") { knobs ->
            val length = knobs.number("Digits", 6f, 4f..8f, steps = 3).toInt()
            var code by remember { mutableStateOf("") }
            val short = code.isNotEmpty() && code.length < length
            Stack {
                OtpField(
                    value = code,
                    onValueChange = { code = it },
                    length = length,
                    label = "Code",
                    helper = "Sent to your phone",
                    isError = short,
                    supportingText = "Enter all $length digits".takeIf { short },
                )
            }
        }

        story("Input group") { _ ->
            var voucher by remember { mutableStateOf("") }
            Stack {
                InputGroup {
                    TextField(
                        value = voucher,
                        onValueChange = { voucher = it },
                        label = "Voucher",
                        modifier = Modifier.weight(1f),
                    )
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
