package com.softistx.material.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.icon.Icon
import com.softistx.material.icon.IconSize
import com.softistx.material.icon.StrangeIcons
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * A drop zone. The library does not pick a file — there is no one picker on every platform — so
 * [onClick] is the host's. This is the chrome: a dashed well, a label, and a hint, so every
 * upload in an app looks like the same well.
 */
@Composable
fun UploadField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Drop a file or browse",
    supporting: String? = "PDF, PNG or JPEG, up to 10 MB",
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.large
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .dashedBorder(1.dp, outline, shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(StrangeTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        Icon(icon = StrangeIcons.Add, description = null, size = IconSize.XLarge)
        Typography(text = label, variant = TypographyVariant.TitleSmall)
        if (supporting != null) {
            Typography(text = supporting, emphasis = Emphasis.Medium)
        }
    }
}

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    shape: Shape,
): Modifier =
    drawWithContent {
        drawContent()
        val stroke =
            Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rounded -> {
                val path = Path().apply { addRoundRect(outline.roundRect) }
                drawPath(path, color = color, style = stroke)
            }

            is Outline.Rectangle -> {
                drawRect(color = color, style = stroke)
            }

            is Outline.Generic -> {
                drawPath(outline.path, color = color, style = stroke)
            }
        }
    }
