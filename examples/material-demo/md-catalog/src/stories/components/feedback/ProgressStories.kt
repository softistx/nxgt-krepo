package com.softistx.material.demo.stories.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.feedback.LabeledProgress
import com.softistx.material.components.feedback.LoadingMark
import com.softistx.material.components.feedback.Progress
import com.softistx.material.components.feedback.ProgressKind
import com.softistx.material.components.feedback.Toaster
import com.softistx.material.components.feedback.TypingIndicator
import com.softistx.material.components.feedback.rememberToasterState
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/** The Progress stories of the Feedback group. */
internal fun StoryGroupBuilder.progressStories() {
    story("Progress") { knobs ->
        val determinate = knobs.flag("Determinate", false)
        val kind = knobs.enumChoice("Kind", ProgressKind.Linear)
        Progress(progress = if (determinate) 0.45f else null, kind = kind)
    }

    story("Labeled progress") { knobs ->
        val determinate = knobs.flag("Determinate", true)
        LabeledProgress(
            progress = if (determinate) 0.45f else null,
            caption = "Uploading invoice.pdf",
            kind = knobs.enumChoice("Kind", ProgressKind.Linear),
        )
    }

    story("Loading mark") { knobs ->
        val determinate = knobs.flag("Determinate", false)
        LoadingMark(
            progress = if (determinate) 0.45f else null,
            contained = knobs.flag("Contained", false),
        )
    }

    story("Typing indicator") { knobs ->
        TypingIndicator(label = knobs.text("Label", "Amara is typing"))
    }

    story("Toast") { _ ->
        val toaster = rememberToasterState()
        Toaster(state = toaster, modifier = Modifier.height(280.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
                Button(text = "Saved", onClick = { toaster.show("Order saved", Tone.Success) })
                Button(text = "Warning", onClick = { toaster.show("Payout delayed", Tone.Warning) })
                Button(
                    text = "Error",
                    onClick = { toaster.show("Could not reach the bank", Tone.Error) },
                )
            }
        }
    }
}
