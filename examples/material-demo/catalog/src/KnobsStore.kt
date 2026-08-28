package com.strange.material.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.strange.material.demo.knobs.Knobs

/**
 * One [Knobs] per story, kept for as long as the catalogue is open.
 *
 * Sharing a single instance across stories would be cheaper and wrong: the stage crossfades, so
 * for a few frames the outgoing and incoming stories compose together and would register their
 * knobs into the same panel. Keeping them apart also means a story remembers what the reader
 * dialled into it when they come back.
 */
@Stable
class KnobsStore {
    private val byStory = mutableMapOf<String, Knobs>()

    fun of(story: Story): Knobs = byStory.getOrPut(story.id) { Knobs() }
}

@Composable
fun rememberKnobsStore(): KnobsStore = remember { KnobsStore() }
