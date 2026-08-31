package com.strange.material.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import com.strange.material.demo.knobs.KnobsPanel
import com.strange.material.navigation.AdaptiveNavDisplay
import com.strange.material.navigation.ListDetail

private sealed interface CatalogRoute

private data object CatalogList : CatalogRoute

private data class CatalogStory(
    val id: String,
) : CatalogRoute

/**
 * The catalogue, laid out by Navigation 3 list-detail rather than a hand-written `when` on width.
 *
 * Compact: the list, then the story (system back pops). Expanded: list and story side by side.
 * The knobs travel with the story — they are the story's controls, not a third destination.
 */
@Composable
fun CatalogScaffold(
    state: CatalogState,
    groups: List<StoryGroup>,
    modifier: Modifier = Modifier,
) {
    val stories = remember(groups) { groups.flatMap(StoryGroup::stories) }
    val knobs = rememberKnobsStore()
    val backStack =
        remember {
            mutableStateListOf<CatalogRoute>(CatalogList, CatalogStory(state.storyId))
        }

    fun open(story: Story) {
        state.storyId = story.id
        val last = backStack.lastOrNull()
        if (last is CatalogStory) {
            backStack[backStack.lastIndex] = CatalogStory(story.id)
        } else {
            backStack.add(CatalogStory(story.id))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CatalogTopBar(state = state)
        HorizontalDivider()
        AdaptiveNavDisplay(
            backStack = backStack,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onBack = {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            },
            entryProvider =
                entryProvider {
                    entry<CatalogList>(
                        metadata = ListDetail.list(placeholderTitle = "Pick a story"),
                    ) {
                        StoryList(
                            groups = groups,
                            selectedId = state.storyId,
                            onSelect = { open(it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    entry<CatalogStory>(metadata = ListDetail.detail()) { route ->
                        val story = stories.firstOrNull { it.id == route.id } ?: stories.first()
                        Column(Modifier.fillMaxSize()) {
                            StoryStage(story = story, knobs = knobs, modifier = Modifier.weight(1f))
                            HorizontalDivider()
                            KnobsPanel(knobs = knobs.of(story), modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
        )
    }
}
