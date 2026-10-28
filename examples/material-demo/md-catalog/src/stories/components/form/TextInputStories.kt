package com.softistx.material.demo.stories.components.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.form.Autocomplete
import com.softistx.material.components.form.SelectField
import com.softistx.material.components.form.TextField
import com.softistx.material.components.form.TextareaField
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Typography

/** The TextInput stories of the Forms group. */
internal fun StoryGroupBuilder.textInputStories() {
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

    story("Autocomplete") { knobs ->
        val complaining = knobs.flag("Complaint", false)
        var query by remember { mutableStateOf("") }
        Stack {
            Autocomplete(
                value = query,
                onValueChange = { query = it },
                options = listOf("Amara Diallo", "Jonas Weber", "Priya Raman", "Chen Wei"),
                onSelect = { query = it },
                label = "Customer",
                placeholder = "Type a name",
                isError = complaining,
                supportingText = if (complaining) "Pick a customer from the list" else null,
            )
        }
    }
}
