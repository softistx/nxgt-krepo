package com.softistx.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/**
 * Chrome around a URL. Material 3 has no link card.
 *
 * The library does not fetch Open Graph — [leading] is the thumbnail the host already has, the
 * same split as `VideoSurface`. Title, address and a line of description are what every preview
 * actually shows.
 */
@Composable
fun LinkPreview(
    title: String,
    url: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    Card(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Typography(text = title, variant = TypographyVariant.TitleSmall)
                Typography(text = url, variant = TypographyVariant.Caption, emphasis = Emphasis.Medium)
                if (description != null) {
                    Typography(text = description, emphasis = Emphasis.Medium)
                }
            }
        }
    }
}
