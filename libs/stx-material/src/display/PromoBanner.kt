package com.strange.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.button.CopyButton
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/**
 * A promotional strip with an optional code to copy. An [AnnouncementBar] wearing a coupon.
 *
 * [code] is the token the reader pastes at checkout; [CopyButton] does the clipboard work. Missing
 * [onDismiss] means the promo cannot be put away.
 */
@Composable
fun PromoBanner(
    text: String,
    modifier: Modifier = Modifier,
    code: String? = null,
    tone: Tone = Tone.Success,
    style: Style = Style,
    onDismiss: (() -> Unit)? = null,
) {
    AnnouncementBar(
        text = text,
        modifier = modifier,
        tone = tone,
        style = style,
        onDismiss = onDismiss,
        action =
            if (code == null) {
                null
            } else {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
                    ) {
                        Typography(text = code, variant = TypographyVariant.Code)
                        CopyButton(text = code, description = "Copy $code")
                    }
                }
            },
    )
}
