package com.strange.material.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/** Two letters from a name, for an [Avatar] that has no image. */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts.last().first()}".uppercase()
    }
}

/**
 * A face, or the initials when there is no image.
 *
 * Coil loads [image] when it is set. [tone] draws a status ring — the presence of a person, not
 * a badge on top of them.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    image: Any? = null,
    size: Dp = 40.dp,
    tone: Tone? = null,
    description: String? = name,
) {
    val ring = tone?.let { StrangeTheme.colors.tone(it).main }
    val shape = CircleShape
    Box(
        modifier =
            modifier
                .size(size)
                .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
                .clip(shape)
                .background(StrangeTheme.colors.scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = description,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Typography(text = initials(name), variant = TypographyVariant.LabelLarge)
        }
    }
}
