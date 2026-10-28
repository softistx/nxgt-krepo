package com.softistx.material.demo.stories.components.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.form.Checkbox
import com.softistx.material.components.form.CheckboxGroup
import com.softistx.material.components.form.RadioGroup
import com.softistx.material.components.form.SliderField
import com.softistx.material.components.form.Switch
import com.softistx.material.demo.StoryGroupBuilder

/** The ChoiceInput stories of the Forms group. */
internal fun StoryGroupBuilder.choiceInputStories() {
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
}
