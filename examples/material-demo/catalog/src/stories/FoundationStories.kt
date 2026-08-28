package com.strange.material.demo.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.strange.material.demo.knobs.enumChoice
import com.strange.material.demo.storyGroup
import com.strange.material.icon.Icon
import com.strange.material.icon.IconSize
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

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

        story("Radii") { _ ->
            val radii = StrangeTheme.radii
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
            ) {
                listOf(
                    "none" to radii.none,
                    "sm" to radii.sm,
                    "md" to radii.md,
                    "lg" to radii.lg,
                    "xl" to radii.xl,
                    "xxl" to radii.xxl,
                    "full" to radii.full,
                ).forEach { (name, radius) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(radius))
                                    .background(StrangeTheme.colors.scheme.primaryContainer),
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
