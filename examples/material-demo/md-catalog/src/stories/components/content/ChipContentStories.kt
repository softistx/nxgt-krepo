package com.softistx.material.demo.stories.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.content.ActionChip
import com.softistx.material.components.content.CodeBlock
import com.softistx.material.components.content.ExpandableText
import com.softistx.material.components.content.FileChip
import com.softistx.material.components.content.Kbd
import com.softistx.material.components.content.SectionHeader
import com.softistx.material.components.content.SuggestionChip
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme

/** The ChipContent stories of the Content group. */
internal fun StoryGroupBuilder.chipContentStories() {
    story("Action chip") { _ ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
            ActionChip(
                text = "Call",
                onClick = {},
                leading = { Icon(icon = StxIcons.Person, description = null) },
            )
            ActionChip(text = "Add to calendar", onClick = {})
            ActionChip(text = "Open in maps", onClick = {})
        }
    }

    story("Keyboard shortcut") { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
            Kbd(keys = listOf("Ctrl", "K"))
            Kbd(keys = listOf("⌘", "⇧", "P"))
        }
    }

    story("Suggestion chip") { _ ->
        var query by remember { mutableStateOf("") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
            listOf("Amara Diallo", "Jonas Weber", "Priya Raman").forEach { name ->
                SuggestionChip(text = name, onClick = { query = name })
            }
        }
        if (query.isNotEmpty()) {
            Typography(text = query, emphasis = Emphasis.Medium)
        }
    }

    story("File chip") { _ ->
        var files by remember { mutableStateOf(listOf("invoice.pdf" to "240 KB", "brief.docx" to "1.2 MB")) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
            files.forEach { (name, size) ->
                FileChip(
                    name = name,
                    sizeLabel = size,
                    onRemove = { files = files.filterNot { it.first == name } },
                )
            }
        }
    }

    story("Section header") { _ ->
        SectionHeader(
            title = "Recent orders",
            supporting = "Last 7 days",
            action = { Button(text = "See all", onClick = {}, variant = ButtonVariant.Link) },
        )
    }

    story("Code block") { _ ->
        CodeBlock(text = "curl https://api.softistx.dev/orders/ord_9f3a")
    }

    story("Expandable text") { knobs ->
        ExpandableText(
            text =
                knobs.text(
                    "Text",
                    "The payout was held because two invoices could not be reconciled. " +
                        "Amara flagged both on Tuesday; the bank has not answered. " +
                        "Until they do, the balance stays in reserve and the dashboard " +
                        "will keep showing this note on every order in the batch.",
                ),
            collapsedLines = knobs.number("Lines", 3f, 1f..6f, steps = 4).toInt(),
        )
    }
}
