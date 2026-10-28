package com.softistx.material.demo.stories.components.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.form.CopyField
import com.softistx.material.components.form.InlineEdit
import com.softistx.material.components.form.InputGroup
import com.softistx.material.components.form.OtpField
import com.softistx.material.components.form.PasswordMeter
import com.softistx.material.components.form.QuantityField
import com.softistx.material.components.form.TagField
import com.softistx.material.components.form.TextField
import com.softistx.material.components.form.UploadField
import com.softistx.material.demo.StoryGroupBuilder

/** The Field stories of the Forms group. */
internal fun StoryGroupBuilder.fieldStories() {
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
}
