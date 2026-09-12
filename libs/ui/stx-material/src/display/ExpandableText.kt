package com.softistx.material.display

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonVariant
import com.softistx.material.text.TypographyVariant
import com.softistx.material.text.style

/**
 * A paragraph that can be asked for more. Material 3 has no "read more".
 *
 * Collapsed, it is [collapsedLines] of [TypographyVariant.BodyMedium]. The toggle only appears
 * when the text actually overflows — a short paragraph is not a control.
 *
 * Layout is read off `Text` directly: [com.softistx.material.text.Typography] does not expose
 * `onTextLayout`, and adding it for this one case would leak a measurement API onto every label.
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 3,
    more: String = "Read more",
    less: String = "Show less",
) {
    var expanded by remember { mutableStateOf(false) }
    var canExpand by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = text,
            style = TypographyVariant.BodyMedium.style(),
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (!expanded) canExpand = layout.hasVisualOverflow
            },
        )
        if (canExpand) {
            Button(
                text = if (expanded) less else more,
                onClick = { expanded = !expanded },
                variant = ButtonVariant.Link,
            )
        }
    }
}
