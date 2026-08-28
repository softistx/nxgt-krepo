package com.strange.material.demo.stories

import com.strange.material.demo.knobs.enumChoice
import com.strange.material.demo.storyGroup

val ScreenStories =
    storyGroup("Screens") {
        story("Orders screen") { knobs ->
            OrdersScreen(
                state = knobs.enumChoice("State", ScreenState.Loaded),
                alert = knobs.flag("Reconciliation alert", true),
            )
        }
    }
