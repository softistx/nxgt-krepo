package com.strange.material.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.display.ListTile
import com.strange.material.display.StatusBadge
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

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
    LazyColumn(
        modifier = modifier.padding(vertical = StrangeTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
    ) {
        groups.forEach { group ->
            item(key = "group:${group.name}") {
                Typography(
                    text = group.name,
                    variant = TypographyVariant.Overline,
                    emphasis = Emphasis.Subtle,
                    modifier =
                        Modifier.padding(
                            start = StrangeTheme.spacing.md,
                            top = StrangeTheme.spacing.md,
                            bottom = StrangeTheme.spacing.xs,
                        ),
                )
            }
            items(group.stories.size, key = { group.stories[it].id }) { index ->
                val story = group.stories[index]
                ListTile(
                    title = story.name,
                    onClick = { onSelect(story) },
                    modifier = Modifier.padding(horizontal = StrangeTheme.spacing.sm),
                    trailing = {
                        if (story.id == selectedId) StatusBadge(text = "open", tone = Tone.Info)
                    },
                )
            }
        }
    }
}
