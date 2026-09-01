package com.softistx.material.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.display.ListTile
import com.softistx.material.display.StatusBadge
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme
import com.softistx.material.theme.Tone

/**
 * The left pane. It is written with `ListTile` and `StatusBadge` on purpose: the catalogue's own
 * chrome is the first consumer of the library, so anything awkward here is a defect there.
 */
@Composable
fun StoryList(
    groups: List<StoryGroup>,
    selectedId: String,
    onSelect: (Story) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(vertical = StxTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs),
        ) {
            groups.forEach { group ->
                item(key = "group:${group.name}") {
                    Typography(
                        text = group.name,
                        variant = TypographyVariant.Overline,
                        emphasis = Emphasis.Subtle,
                        modifier =
                            Modifier.padding(
                                start = StxTheme.spacing.md,
                                top = StxTheme.spacing.md,
                                bottom = StxTheme.spacing.xs,
                            ),
                    )
                }
                items(group.stories.size, key = { group.stories[it].id }) { index ->
                    val story = group.stories[index]
                    ListTile(
                        title = story.name,
                        onClick = { onSelect(story) },
                        modifier = Modifier.padding(horizontal = StxTheme.spacing.sm),
                        trailing = {
                            if (story.id == selectedId) StatusBadge(text = "open", tone = Tone.Info)
                        },
                    )
                }
            }
        }
        PaneScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
