package com.softistx.material.demo.stories.components.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.button.ConfirmButton
import com.softistx.material.components.form.CheckItem
import com.softistx.material.components.form.CheckState
import com.softistx.material.components.form.Checklist
import com.softistx.material.components.form.Composer
import com.softistx.material.components.form.DangerZone
import com.softistx.material.components.form.FormSection
import com.softistx.material.components.form.TextField
import com.softistx.material.components.form.ThemeToggle
import com.softistx.material.components.form.TriStateCheckbox
import com.softistx.material.components.form.cycleCheckState
import com.softistx.material.components.form.toggleCheckItem
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.ColorMode

/** The FormLayout stories of the Forms group. */
internal fun StoryGroupBuilder.formLayoutStories() {
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
