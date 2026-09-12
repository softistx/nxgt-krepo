package com.softistx.material.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.navigation3.runtime.entryProvider
import com.softistx.material.display.ListTile
import com.softistx.material.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * [AdaptiveNavDisplay] composed once. A missing SceneStrategy or a placeholder that cannot be
 * composed is a crash at display time, not at compile time.
 */
class Nav3RenderTest :
    FeatureSpec({
        feature("list-detail") {
            scenario("renders the list pane without throwing") {
                ink() shouldBeGreaterThan 0
            }
        }
    })

private data object Inbox

private fun ink(): Int {
    val scene = ImageComposeScene(width = 800, height = 600)
    try {
        scene.setContent {
            StxTheme(isDark = false) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val backStack = remember { mutableStateListOf(Inbox) }
                    AdaptiveNavDisplay(
                        backStack = backStack,
                        entryProvider =
                            entryProvider {
                                entry<Inbox>(metadata = ListDetail.list()) {
                                    ListTile(
                                        title = "Amara Diallo",
                                        supporting = "Payout delayed",
                                    )
                                }
                            },
                    )
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
