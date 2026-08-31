package com.strange.material.navigation

import androidx.compose.foundation.background
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
import com.strange.material.icon.StrangeIcons
import com.strange.material.theme.StrangeTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * Every navigation control composed once, for the class of failure that compiles and then throws
 * at render time — an experimental M3 bar that needs a scroll behavior, a search bar whose
 * expansion measures badly.
 */
class NavigationRenderTest :
    FeatureSpec({
        feature("every control in the package") {
            scenario("renders without throwing, and paints something") {
                ink() shouldBeGreaterThan 0
            }
        }
    })

private fun ink(): Int {
    val scene = ImageComposeScene(width = 720, height = 1100)
    try {
        scene.setContent {
            StrangeTheme(isDark = false) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.White).padding(16.dp),
                ) {
                    chrome()
                }
            }
        }
        var time = 0L
        repeat(8) {
            time += 100_000_000L
            scene.render(time)
        }
        val map = scene.render(time).toComposeImageBitmap().toPixelMap()
        var painted = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                if (map[x, y] != Color.White) painted++
            }
        }
        return painted
    } finally {
        scene.close()
    }
}

@Composable
private fun chrome() {
    AppBar(title = "Orders", navigationIcon = StrangeIcons.Menu, onNavigation = {})
    Search(query = "", onQueryChange = {})
    Tabs(labels = listOf("Paid", "Pending"), selected = 0, onSelect = {})
    SegmentedControl(options = listOf("Day", "Week"), selected = 0, onSelect = {})
    Breadcrumb(
        items =
            listOf(
                BreadcrumbItem("Home", onClick = {}),
                BreadcrumbItem("Workspace", onClick = {}),
                BreadcrumbItem("Projects", onClick = {}),
                BreadcrumbItem("Northwind", onClick = {}),
                BreadcrumbItem("Orders"),
            ),
    )
    Stepper(
        steps = listOf(Step("Account"), Step("Plan"), Step("Pay")),
        current = 1,
        onStep = {},
    )
}
