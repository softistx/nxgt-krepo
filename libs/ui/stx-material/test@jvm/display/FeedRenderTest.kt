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
import com.softistx.material.feedback.TypingIndicator
import com.softistx.material.form.CheckItem
import com.softistx.material.form.Checklist
import com.softistx.material.form.Composer
import com.softistx.material.media.PersonCard
import com.softistx.material.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

class FeedRenderTest :
    FeatureSpec({
        feature("every new people and feed control") {
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
    PersonCard(name = "Amara Diallo", supporting = "Finance")
    Composer(value = "Hello", onValueChange = {}, onSend = {}, onAttach = {})
    TypingIndicator()
    ReactionBar(reactions = listOf(Reaction("👍", 3, selected = true)))
    AnnouncementBar(text = "The bank is delayed.", onDismiss = {})
    QuoteBlock(text = "Ship it.", attribution = "Amara")
    LinkPreview(title = "Orders API", url = "https://api.softistx.dev/orders")
    Checklist(items = listOf(CheckItem("Pack", true), CheckItem("Ship")), onToggle = {})
}
