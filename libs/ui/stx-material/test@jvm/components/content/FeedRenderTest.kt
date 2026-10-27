package com.softistx.material.components.content

import androidx.compose.runtime.Composable
import com.softistx.material.components.feedback.AnnouncementBar
import com.softistx.material.components.feedback.TypingIndicator
import com.softistx.material.components.form.CheckItem
import com.softistx.material.components.form.Checklist
import com.softistx.material.components.form.Composer
import com.softistx.material.components.media.PersonCard
import com.softistx.material.renderPage
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

private fun render(content: @Composable () -> Unit): Int = renderPage(480, 1600, content)

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
