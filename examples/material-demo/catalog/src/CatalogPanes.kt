package com.strange.material.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.button.Button
import com.strange.material.button.ButtonVariant
import com.strange.material.demo.knobs.KnobsPanel
import com.strange.material.motion.Transitions

private val ListPaneWidth = 260.dp
private val KnobsPaneWidth = 300.dp

/** Groups, preview and controls all visible — the layout the catalogue is designed around. */
@Composable
fun ThreePane(
    state: CatalogState,
    groups: List<StoryGroup>,
    story: Story,
    knobs: KnobsStore,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        StoryList(
            groups = groups,
            selectedId = story.id,
            onSelect = { state.storyId = it.id },
            modifier = Modifier.width(ListPaneWidth),
        )
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        StoryStage(story = story, knobs = knobs, modifier = Modifier.weight(1f))
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        KnobsPanel(knobs = knobs.of(story), modifier = Modifier.width(KnobsPaneWidth))
    }
}

/** The controls fold under the preview, which is the pane that can afford the height. */
@Composable
fun TwoPane(
    state: CatalogState,
    groups: List<StoryGroup>,
    story: Story,
    knobs: KnobsStore,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        StoryList(
            groups = groups,
            selectedId = story.id,
            onSelect = { state.storyId = it.id },
            modifier = Modifier.width(ListPaneWidth),
        )
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        Column(modifier = Modifier.weight(1f)) {
            StoryStage(story = story, knobs = knobs, modifier = Modifier.weight(1f))
            HorizontalDivider()
            KnobsPanel(knobs = knobs.of(story), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** One pane at a time: the list, or the story the reader opened, with a way back. */
@Composable
fun SinglePane(
    state: CatalogState,
    groups: List<StoryGroup>,
    story: Story,
    knobs: KnobsStore,
) {
    AnimatedVisibility(visible = !state.opened, enter = Transitions.fade, exit = Transitions.fadeAway) {
        StoryList(
            groups = groups,
            selectedId = story.id,
            onSelect = {
                state.storyId = it.id
                state.opened = true
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
    AnimatedVisibility(visible = state.opened, enter = Transitions.riseIn, exit = Transitions.fadeAway) {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                text = "Back to stories",
                onClick = { state.opened = false },
                variant = ButtonVariant.Link,
            )
            StoryStage(story = story, knobs = knobs, modifier = Modifier.weight(1f))
            HorizontalDivider()
            KnobsPanel(knobs = knobs.of(story), modifier = Modifier.fillMaxWidth())
        }
    }
}
