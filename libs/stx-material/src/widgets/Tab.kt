package com.strange.material.widgets

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Tab(
    modifier: Modifier = Modifier,
    style: Style = Style,
    selected: Boolean = false,
    enabled: Boolean = true,
    label: String,
    icon: ImageVector? = null,
    selectedIcon: ImageVector? = null,
    iconContentDescription: String? = null,
    onClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme

    val interactionSource = remember { MutableInteractionSource() }

    val styleState =
        rememberUpdatedStyleState(interactionSource) {
            it.isSelected = selected
        }

    val tabStyle =
        Style {
            background(colorScheme.primary.copy(.1f))
            selected {
                background(colorScheme.primary.copy(.3f))
            }
        }

    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .styleable(styleState, tabStyle, style)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            Modifier
                .padding(4.dp)
                .width(70.dp)
                .then(modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                AnimatedContent(selected, label = "Custom Tab Icon") { targetState ->
                    if (targetState) {
                        Icon(
                            selectedIcon ?: icon,
                            iconContentDescription,
                            modifier = Modifier.size(20.dp),
                            tint = colorScheme.primary,
                        )
                    } else {
                        Icon(
                            icon,
                            iconContentDescription,
                            modifier = Modifier.size(20.dp),
                            tint = colorScheme.onBackground,
                        )
                    }
                }
            }
            Text(
                label,
                color = if (selected) colorScheme.primary else Color.Unspecified,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
