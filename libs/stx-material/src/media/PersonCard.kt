package com.strange.material.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.display.Card
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/**
 * A person as a card: face, name, a line of meta, and an optional action.
 *
 * A `ListTile` is a row in a list. `EntityHeader` is the top of a page.
 * This is the compact identity that sits in a grid, a mention, a search hit.
 */
@Composable
fun PersonCard(
    name: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    image: Any? = null,
    tone: Tone? = null,
    onClick: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Card(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
        ) {
            Avatar(name = name, image = image, size = 48.dp, tone = tone)
            Column(modifier = Modifier.weight(1f)) {
                Typography(text = name, variant = TypographyVariant.TitleSmall)
                if (supporting != null) {
                    Typography(text = supporting, emphasis = Emphasis.Medium)
                }
            }
            action?.invoke()
        }
    }
}
