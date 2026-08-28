package com.strange.material.demo

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The catalogue, laid out for the window it is in: three panes on a desktop, two on a tablet, one
 * at a time on a phone. The breakpoints are read from the pane the catalogue actually occupies —
 * not from the screen — so a resized desktop window folds the same way a small device does.
 */
@Composable
fun CatalogScaffold(
    state: CatalogState,
    groups: List<StoryGroup>,
    modifier: Modifier = Modifier,
) {
    val stories = remember(groups) { groups.flatMap(StoryGroup::stories) }
    val story =
        remember(state.storyId, stories) {
            stories.firstOrNull { it.id == state.storyId } ?: stories.first()
        }
    val knobs = rememberKnobsStore()

    Column(modifier = modifier.fillMaxSize()) {
        CatalogTopBar(state = state)
        HorizontalDivider()
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            when {
                maxWidth >= ThreePaneWidth -> {
                    ThreePane(state = state, groups = groups, story = story, knobs = knobs)
                }

                maxWidth >= TwoPaneWidth -> {
                    TwoPane(state = state, groups = groups, story = story, knobs = knobs)
                }

                else -> {
                    SinglePane(state = state, groups = groups, story = story, knobs = knobs)
                }
            }
        }
    }
}

private val ThreePaneWidth = 1040.dp
private val TwoPaneWidth = 680.dp
