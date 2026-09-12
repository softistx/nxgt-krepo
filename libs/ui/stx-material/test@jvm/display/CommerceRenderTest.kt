package com.softistx.material.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import com.softistx.material.button.Button
import com.softistx.material.theme.StxTheme
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

private fun render(content: @Composable () -> Unit): Int {
    val scene = ImageComposeScene(width = 480, height = 1600)
    try {
        scene.setContent {
            StxTheme(isDark = false) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.White).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
        var time = 0L
        repeat(8) {
            time += 100_000_000L
            scene.render(time)
        }
        val map = scene.render(time).toComposeImageBitmap().toPixelMap()
        var ink = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                if (map[x, y] != Color.White) ink++
            }
        }
        return ink
    } finally {
        scene.close()
    }
}

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
