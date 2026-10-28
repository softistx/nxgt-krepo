package com.softistx.material.demo.stories.components.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.feedback.AlertDefaults
import com.softistx.material.components.feedback.AnnouncementBar
import com.softistx.material.components.feedback.PromoBanner
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.foundation.theme.Tone

/** The Banner stories of the Feedback group. */
internal fun StoryGroupBuilder.bannerStories() {
    story("Announcement bar") { knobs ->
        val tone = knobs.enumChoice("Tone", Tone.Warning)
        var visible by remember { mutableStateOf(true) }
        if (visible) {
            AnnouncementBar(
                text = knobs.text("Text", "The bank is delayed until Tuesday."),
                tone = tone,
                icon = if (knobs.flag("Icon", true)) AlertDefaults.icon(tone) else null,
                onDismiss = { visible = false },
                action = { Button(text = "Details", onClick = {}, variant = ButtonVariant.Link) },
            )
        } else {
            Button(text = "Show again", onClick = { visible = true }, variant = ButtonVariant.Ghost)
        }
    }

    story("Promo banner") { knobs ->
        val tone = knobs.enumChoice("Tone", Tone.Success)
        var visible by remember { mutableStateOf(true) }
        if (visible) {
            PromoBanner(
                text = knobs.text("Text", "Spring sale — 20% off the first year."),
                code = knobs.text("Code", "SPRING20").ifBlank { null },
                tone = tone,
                icon = if (knobs.flag("Icon", true)) AlertDefaults.icon(tone) else null,
                onDismiss = { visible = false },
            )
        } else {
            Button(text = "Show again", onClick = { visible = true }, variant = ButtonVariant.Ghost)
        }
    }
}
