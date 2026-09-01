package com.softistx.material.demo.stories

import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.demo.storyGroup

val ScreenStories =
    storyGroup("Screens") {
        story("Orders screen") { knobs ->
            OrdersScreen(
                state = knobs.enumChoice("State", ScreenState.Loaded),
                alert = knobs.flag("Reconciliation alert", true),
            )
        }

        story("Sign-up form") { _ ->
            SignUpScreen()
        }

        story("Checkout") { _ ->
            CheckoutScreen()
        }
    }
