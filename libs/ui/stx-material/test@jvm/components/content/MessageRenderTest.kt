package com.softistx.material.components.content

import androidx.compose.runtime.Composable
import com.softistx.material.components.media.AvatarItem
import com.softistx.material.components.media.SeenBy
import com.softistx.material.renderPage
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

class MessageRenderTest :
    FeatureSpec({
        feature("every new message-thread control") {
            scenario("renders without throwing, and paints something") {
                render { paint() } shouldBeGreaterThan 0
            }
        }
    })

private fun render(content: @Composable () -> Unit): Int = renderPage(480, 1600, content)

@Composable
private fun paint() {
    MessageBubble(text = "The payout landed.", name = "Amara Diallo", meta = "14:02")
    MessageBubble(text = "Noted, thanks.", outgoing = true, name = "You", meta = "Read")
    Comment(name = "Jonas Weber", text = "Ship the small thing.", supporting = "2h ago")
    ReplyPreview(name = "Amara Diallo", text = "The payout landed.", onDismiss = {})
    PinBar(text = "Office closed on Monday.", onDismiss = {})
    MentionChip(name = "Priya Raman", onClick = {}, onRemove = {})
    SeenBy(items = listOf(AvatarItem("Amara Diallo"), AvatarItem("Jonas Weber")))
}
