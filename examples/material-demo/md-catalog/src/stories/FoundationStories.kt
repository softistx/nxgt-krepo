package com.softistx.material.demo.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.softistx.material.demo.knobs.enumChoice
import com.softistx.material.demo.storyGroup
import com.softistx.material.icon.Icon
import com.softistx.material.icon.IconSize
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme
import com.softistx.material.theme.Tone

val FoundationStories =
    storyGroup("Foundation") {
        story("Typography") { knobs ->
            val emphasis = knobs.enumChoice("Emphasis", Emphasis.Full)
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                TypographyVariant.entries.forEach { variant ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Typography(
                            text = variant.name,
                            variant = TypographyVariant.Code,
                            emphasis = Emphasis.Subtle,
                            modifier = Modifier.width(140.dp),
                        )
                        Typography(text = "Grumpy wizards", variant = variant, emphasis = emphasis)
                    }
                }
            }
        }

        story("Spacing") { _ ->
            val spacing = StrangeTheme.spacing
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm)) {
                listOf(
                    "none" to spacing.none,
                    "xxs" to spacing.xxs,
                    "xs" to spacing.xs,
                    "sm" to spacing.sm,
                    "md" to spacing.md,
                    "lg" to spacing.lg,
                    "xl" to spacing.xl,
                    "xxl" to spacing.xxl,
                ).forEach { (name, size) -> TokenBar(name = name, size = size) }
            }
        }

        story("Shapes") { _ ->
            val shapes = MaterialTheme.shapes
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
            ) {
                // All eight of Material 3's slots, including the three that arrived with
                // expressive — a hand-written Shapes(...) fills only five and quietly leaves
                // largeIncreased, extraLargeIncreased and extraExtraLarge on their defaults.
                listOf(
                    "extraSmall" to shapes.extraSmall,
                    "small" to shapes.small,
                    "medium" to shapes.medium,
                    "large" to shapes.large,
                    "largeIncreased" to shapes.largeIncreased,
                    "extraLarge" to shapes.extraLarge,
                    "extraLargeIncreased" to shapes.extraLargeIncreased,
                    "extraExtraLarge" to shapes.extraExtraLarge,
                ).forEach { (name, shape) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                        )
                        Typography(text = name, variant = TypographyVariant.Caption)
                    }
                }
            }
        }

        story("Icons") { knobs ->
            val size = knobs.enumChoice("Size", IconSize.Large)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
            ) {
                CatalogIcons.forEach { (name, icon) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
                    ) {
                        Icon(icon = icon, description = name, size = size)
                        Typography(text = name, variant = TypographyVariant.Caption)
                    }
                }
            }
        }

        story("Semantic colours") { _ ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
            ) {
                Tone.entries.forEach { tone ->
                    val role = StrangeTheme.colors.tone(tone)
                    Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs)) {
                        Typography(text = tone.name, variant = TypographyVariant.LabelLarge)
                        Swatch("main", role.main)
                        Swatch("container", role.container)
                    }
                }
            }
        }
    }
