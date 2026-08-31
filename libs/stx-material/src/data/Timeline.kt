package com.strange.material.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

@Immutable
data class TimelineItem(
    val title: String,
    val at: String,
    val body: String? = null,
)

/**
 * A vertical activity trail. Not a [com.strange.material.navigation.Stepper]: every item already
 * happened, and none of them is "current".
 */
@Composable
fun Timeline(
    items: List<TimelineItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    if (index != items.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(StrangeTheme.spacing.xl)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Column {
                    Typography(text = item.title, variant = TypographyVariant.TitleSmall)
                    Typography(text = item.at, variant = TypographyVariant.LabelSmall, emphasis = Emphasis.Subtle)
                    if (item.body != null) {
                        Typography(text = item.body)
                    }
                }
            }
        }
    }
}
