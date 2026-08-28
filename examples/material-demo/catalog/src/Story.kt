package com.strange.material.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.strange.material.demo.knobs.Knobs

/**
 * One entry of the catalogue: a name, and a composable that renders the component with whatever
 * the reader has dialled into [Knobs].
 *
 * The [id] is derived rather than given, so a story cannot be registered twice under two spellings
 * and the selection can be held as a plain `String` across a window resize.
 */
@Immutable
class Story(
    val group: String,
    val name: String,
    val content: @Composable (Knobs) -> Unit,
) {
    val id: String = "${slug(group)}/${slug(name)}"
}

/** A group of stories, which is also how the left pane is sectioned. */
@Immutable
class StoryGroup(
    val name: String,
    val stories: List<Story>,
)

/**
 * Declares a group. The builder exists so a story file reads as a list of stories rather than as a
 * list of constructor calls repeating their own group name.
 */
fun storyGroup(
    name: String,
    build: StoryGroupBuilder.() -> Unit,
): StoryGroup = StoryGroup(name, StoryGroupBuilder(name).apply(build).stories())

class StoryGroupBuilder(
    private val group: String,
) {
    private val stories = mutableListOf<Story>()

    fun story(
        name: String,
        content: @Composable (Knobs) -> Unit,
    ) {
        stories += Story(group, name, content)
    }

    internal fun stories(): List<Story> = stories.toList()
}

private fun slug(value: String): String = value.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
