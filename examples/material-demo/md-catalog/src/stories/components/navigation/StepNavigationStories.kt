package com.softistx.material.demo.stories.components.navigation

import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Fab
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.navigation.BottomBar
import com.softistx.material.components.navigation.Search
import com.softistx.material.components.navigation.Step
import com.softistx.material.components.navigation.StepFooter
import com.softistx.material.components.navigation.Stepper
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.icon.StxIcons

/** The StepNavigation stories of the Navigation group. */
internal fun StoryGroupBuilder.stepNavigationStories() {
    story("Stepper") { knobs ->
        val current = knobs.number("Current step", 1f, 0f..3f, steps = 2).toInt()
        Stepper(
            steps =
                listOf(
                    Step("Account", "Email and password"),
                    Step("Profile", "Name and photo"),
                    Step("Plan", "Monthly or yearly"),
                    Step("Review", "Confirm and pay"),
                ),
            current = current,
            onStep = { knobs.set("Current step", it.toFloat()) },
            collapseBelow = if (knobs.flag("Force vertical", false)) 10_000.dp else 520.dp,
        )
    }

    story("Bottom bar") { _ ->
        BottomBar(
            fab = { Fab(icon = StxIcons.Add, description = "New order", onClick = {}) },
        ) {
            IconButton(icon = StxIcons.Edit, description = "Edit", onClick = {})
            IconButton(icon = StxIcons.Search, description = "Search", onClick = {})
            IconButton(icon = StxIcons.Delete, description = "Delete", onClick = {})
        }
    }

    story("Step footer") { knobs ->
        StepFooter(
            onNext = {},
            onBack = if (knobs.flag("Show back", true)) ({ }) else null,
            busy = knobs.flag("Busy", false),
        )
    }
}
