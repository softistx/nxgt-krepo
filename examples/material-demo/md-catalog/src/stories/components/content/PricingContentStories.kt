package com.softistx.material.demo.stories.components.content

import com.softistx.material.components.button.Button
import com.softistx.material.components.content.FeatureItem
import com.softistx.material.components.content.FeatureList
import com.softistx.material.components.content.Price
import com.softistx.material.components.content.PricingCard
import com.softistx.material.demo.StoryGroupBuilder
import com.softistx.material.foundation.theme.Tone

/** The PricingContent stories of the Content group. */
internal fun StoryGroupBuilder.pricingContentStories() {
    story("Price") { knobs ->
        Price(
            amount = knobs.text("Amount", "€12"),
            compareAt = knobs.text("Compare at", "€18").ifBlank { null },
            period = knobs.text("Period", "/mo").ifBlank { null },
            tone = if (knobs.flag("Discount tone", true)) Tone.Success else null,
        )
    }

    story("Feature list") { _ ->
        FeatureList(
            items =
                listOf(
                    FeatureItem("Unlimited orders"),
                    FeatureItem("Priority payouts"),
                    FeatureItem("Audit trail"),
                    FeatureItem("Dedicated account manager", included = false),
                ),
        )
    }

    story("Pricing card") { knobs ->
        PricingCard(
            name = knobs.text("Name", "Studio"),
            amount = "€29",
            compareAt = "€39",
            period = "/mo",
            description = "For a team that ships every week.",
            features =
                listOf(
                    FeatureItem("Unlimited orders"),
                    FeatureItem("Priority payouts"),
                    FeatureItem("Dedicated account manager", included = false),
                ),
            highlighted = knobs.flag("Highlighted", true),
            badge = "Popular".takeIf { knobs.flag("Badge", true) },
            action = { Button(text = "Start", onClick = {}) },
        )
    }
}
