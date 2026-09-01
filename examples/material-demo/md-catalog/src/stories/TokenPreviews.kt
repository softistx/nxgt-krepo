package com.softistx.material.demo.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * The two swatch shapes the foundation stories draw their tokens with. They are here rather than
 * beside the stories so neither file has to carry both the catalogue of tokens and the way a token
 * is drawn.
 */
@Composable
internal fun TokenBar(
    name: String,
    size: Dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Typography(
            text = name,
            variant = TypographyVariant.Code,
            modifier = Modifier.width(56.dp),
        )
        Box(
            modifier =
                Modifier
                    .width(size.coerceAtLeast(1.dp))
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Typography(text = "$size", variant = TypographyVariant.Caption, emphasis = Emphasis.Subtle)
    }
}

@Composable
internal fun Swatch(
    name: String,
    color: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(color),
        )
        Typography(text = name, variant = TypographyVariant.Caption)
    }
}
