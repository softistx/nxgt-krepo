package com.softistx.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.button.IconButton
import com.softistx.material.icon.StrangeIcons
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * The one-line quote sitting above a [com.softistx.material.form.Composer].
 *
 * A [QuoteBlock] is a passage in a body of text. This is the chrome of "replying to": name, a
 * truncated line, and an optional dismiss. Material 3's `Surface` plus `VerticalDivider`.
 */
@Composable
fun ReplyPreview(
    name: String,
    text: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(StrangeTheme.spacing.sm)
                    .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
        ) {
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Typography(text = name, variant = TypographyVariant.LabelSmall)
                Typography(
                    text = text,
                    variant = TypographyVariant.BodySmall,
                    emphasis = Emphasis.Medium,
                    maxLines = 1,
                )
            }
            if (onDismiss != null) {
                IconButton(
                    icon = StrangeIcons.Close,
                    description = "Dismiss reply",
                    onClick = onDismiss,
                )
            }
        }
    }
}
