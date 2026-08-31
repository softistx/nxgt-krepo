package com.strange.material.demo.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.button.Button
import com.strange.material.demo.storyGroup
import com.strange.material.media.Avatar
import com.strange.material.media.AvatarGroup
import com.strange.material.media.AvatarItem
import com.strange.material.media.CameraSurface
import com.strange.material.media.Lightbox
import com.strange.material.media.PdfSurface
import com.strange.material.media.PersonCard
import com.strange.material.media.VideoSurface
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

val MediaStories =
    storyGroup("Media") {
        story("Avatar") { knobs ->
            val ring = knobs.flag("Status ring", true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md)) {
                Avatar(name = "Amara Diallo", tone = if (ring) Tone.Success else null)
                Avatar(name = "Jonas Weber", tone = if (ring) Tone.Warning else null)
                Avatar(name = "Priya", tone = if (ring) Tone.Info else null)
            }
        }

        story("Avatar group") { knobs ->
            AvatarGroup(
                items =
                    listOf(
                        AvatarItem("Amara Diallo", tone = Tone.Success),
                        AvatarItem("Jonas Weber"),
                        AvatarItem("Priya Raman", tone = Tone.Info),
                        AvatarItem("Chen Wei"),
                        AvatarItem("Léa Martin"),
                    ),
                max = knobs.number("Max faces", 3f, 1f..5f, steps = 3).toInt(),
            )
        }

        story("Person card") { knobs ->
            PersonCard(
                name = knobs.text("Name", "Amara Diallo"),
                supporting = "Finance · last seen this morning",
                tone = if (knobs.flag("Online", true)) Tone.Success else null,
                onClick = {},
                action = { Button(text = "Message", onClick = {}) },
            )
        }

        story("Video surface") { _ ->
            VideoSurface(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                Typography(
                    text = "Renderer not wired on this host",
                    emphasis = Emphasis.Medium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        story("PDF surface") { _ ->
            PdfSurface(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                Typography(
                    text = "Drop a PdfRenderer in this slot",
                    emphasis = Emphasis.Medium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        story("Camera surface") { _ ->
            CameraSurface(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Typography(
                    text = "Camera preview lives here",
                    emphasis = Emphasis.Medium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        story("Lightbox") { _ ->
            var open by remember { mutableStateOf(false) }
            Column {
                Button(text = "Open lightbox", onClick = { open = true })
                Lightbox(visible = open, model = null, onDismiss = { open = false })
            }
        }
    }
