package com.softistx.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonVariant
import com.softistx.material.button.ConfirmButton
import com.softistx.material.demo.storyGroup
import com.softistx.material.form.Autocomplete
import com.softistx.material.form.CheckItem
import com.softistx.material.form.CheckState
import com.softistx.material.form.Checkbox
import com.softistx.material.form.CheckboxGroup
import com.softistx.material.form.Checklist
import com.softistx.material.form.Composer
import com.softistx.material.form.CopyField
import com.softistx.material.form.DangerZone
import com.softistx.material.form.FormSection
import com.softistx.material.form.InlineEdit
import com.softistx.material.form.InputGroup
import com.softistx.material.form.OtpField
import com.softistx.material.form.PasswordMeter
import com.softistx.material.form.QuantityField
import com.softistx.material.form.RadioGroup
import com.softistx.material.form.SelectField
import com.softistx.material.form.SliderField
import com.softistx.material.form.Switch
import com.softistx.material.form.TagField
import com.softistx.material.form.TextField
import com.softistx.material.form.TextareaField
import com.softistx.material.form.ThemeToggle
import com.softistx.material.form.TriStateCheckbox
import com.softistx.material.form.UploadField
import com.softistx.material.form.cycleCheckState
import com.softistx.material.form.toggleCheckItem
import com.softistx.material.icon.Icon
import com.softistx.material.icon.StxIcons
import com.softistx.material.text.Typography
import com.softistx.material.theme.ColorMode
import com.softistx.material.theme.StxTheme

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
                    leading = { Icon(icon = StxIcons.Person, description = null) },
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

        story("Autocomplete") { _ ->
            var query by remember { mutableStateOf("") }
            Stack {
                Autocomplete(
                    value = query,
                    onValueChange = { query = it },
                    options = listOf("Amara Diallo", "Jonas Weber", "Priya Raman", "Chen Wei"),
                    onSelect = { query = it },
                    label = "Customer",
                    placeholder = "Type a name",
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

        story("Range slider") { _ ->
            var prices by remember { mutableStateOf(20f..80f) }
            Stack {
                SliderField(
                    value = prices,
                    onValueChange = { prices = it },
                    label = "Price",
                    range = 0f..200f,
                    format = { "€${it.toInt()}" },
                )
            }
        }

        story("Tags") { knobs ->
            var tags by remember { mutableStateOf(listOf("urgent", "europe")) }
            Stack {
                TagField(
                    tags = tags,
                    onTagsChange = { tags = it },
                    label = "Labels",
                    placeholder = "Add a label",
                    enabled = knobs.flag("Enabled", true),
                )
            }
        }

        story("Quantity") { knobs ->
            var quantity by remember { mutableStateOf(2) }
            Stack {
                QuantityField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = "Seats",
                    range = 0..12,
                    enabled = knobs.flag("Enabled", true),
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

        story("Upload") { knobs ->
            UploadField(
                onClick = {},
                label = knobs.text("Label", "Drop a file or browse"),
                enabled = knobs.flag("Enabled", true),
            )
        }

        story("Inline edit") { knobs ->
            var title by remember { mutableStateOf("Quarterly report") }
            Stack {
                InlineEdit(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Add a title",
                    enabled = knobs.flag("Enabled", true),
                )
            }
        }

        story("Password meter") { _ ->
            var secret by remember { mutableStateOf("") }
            Stack {
                TextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = "Password",
                    secret = true,
                )
                PasswordMeter(value = secret)
            }
        }

        story("Copy field") { _ ->
            CopyField(value = "ord_9f3a", label = "Order id", helper = "Share this with support")
        }

        story("Tri-state checkbox") { knobs ->
            var state by remember { mutableStateOf(CheckState.Indeterminate) }
            Stack {
                TriStateCheckbox(
                    state = state,
                    onClick = { state = cycleCheckState(state) },
                    label = "All invoices",
                    helper = "On when every child is ticked",
                    enabled = knobs.flag("Enabled", true),
                )
            }
        }

        story("Theme toggle") { _ ->
            var mode by remember { mutableStateOf(ColorMode.System) }
            ThemeToggle(value = mode, onChange = { mode = it })
        }

        story("Form section") { _ ->
            FormSection(title = "Account", supporting = "How we reach you") {
                TextField(value = "ada@example.com", onValueChange = {}, label = "Email")
            }
        }

        story("Danger zone") { _ ->
            DangerZone(text = "The organisation and every order in it will be removed.") {
                ConfirmButton(text = "Delete organisation", onConfirm = {})
            }
        }

        story("Composer") { knobs ->
            var draft by remember { mutableStateOf("") }
            var sent by remember { mutableStateOf<String?>(null) }
            Stack {
                Composer(
                    value = draft,
                    onValueChange = { draft = it },
                    onSend = {
                        sent = draft
                        draft = ""
                    },
                    enabled = knobs.flag("Enabled", true),
                    onAttach = if (knobs.flag("Attach", true)) ({}) else null,
                )
                if (sent != null) {
                    Typography(text = "Sent: $sent")
                }
            }
        }

        story("Checklist") { knobs ->
            var items by remember {
                mutableStateOf(
                    listOf(
                        CheckItem("Pack the order", checked = true),
                        CheckItem("Print the label"),
                        CheckItem("Hand to courier"),
                    ),
                )
            }
            Checklist(
                items = items,
                onToggle = { items = toggleCheckItem(items, it) },
                enabled = knobs.flag("Enabled", true),
            )
        }
    }

/** Every story here is a column of fields; this is that column, once. */
@Composable
private fun Stack(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
    ) {
        content()
    }
}
