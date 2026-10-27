package com.softistx.material.components.content

import androidx.compose.runtime.Composable
import com.softistx.material.components.button.Button
import com.softistx.material.components.feedback.PromoBanner
import com.softistx.material.renderPage
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

class CommerceRenderTest :
    FeatureSpec({
        feature("every new commerce control") {
            scenario("renders without throwing, and paints something") {
                render { paint() } shouldBeGreaterThan 0
            }
        }
    })

private fun render(content: @Composable () -> Unit): Int = renderPage(480, 1600, content)

@Composable
private fun paint() {
    Price(amount = "€12", compareAt = "€18", period = "/mo")
    FeatureList(
        items =
            listOf(
                FeatureItem("Unlimited orders"),
                FeatureItem("Priority payouts"),
                FeatureItem("Dedicated account manager", included = false),
            ),
    )
    PricingCard(
        name = "Studio",
        amount = "€29",
        period = "/mo",
        features = listOf(FeatureItem("Unlimited orders")),
        highlighted = true,
        badge = "Popular",
        action = { Button(text = "Start", onClick = {}) },
    )
    PromoBanner(text = "Spring sale — 20% off the first year.", code = "SPRING20", onDismiss = {})
}
