package com.strange.material.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * The type scale, named once so a caller never assembles a `TextStyle` by hand.
 *
 * Material 3 has fifteen roles and no opinion about what a caption, an overline or a snippet of
 * code should look like; the React library has those and calls them variants. This enum is the
 * union: the M3 roles under their own names, plus the four a product actually needs and M3 leaves
 * out.
 */
enum class TypographyVariant {
    DisplayLarge,
    DisplayMedium,
    DisplaySmall,
    HeadlineLarge,
    HeadlineMedium,
    HeadlineSmall,
    TitleLarge,
    TitleMedium,
    TitleSmall,
    BodyLarge,
    BodyMedium,
    BodySmall,
    LabelLarge,
    LabelMedium,
    LabelSmall,

    /** Small, wide-tracked, upper-case: the label above a group of fields. */
    Overline,

    /** Body text stepped down and dimmed by the caller — a hint under a field, a timestamp. */
    Caption,

    /** A quantity meant to be read at a glance: the number on a summary tile. */
    Metric,

    /** Inline monospace, for an identifier, a key or a snippet. */
    Code,

    /** Body text carrying a link. */
    Link,
}

/**
 * The [TextStyle] a variant resolves to, in the current theme.
 *
 * The M3 roles are read from `MaterialTheme.typography` rather than restated, so an application
 * that supplies its own font sees it everywhere. The four extra variants are derived from an M3
 * role rather than invented, which keeps them on the same scale when that font changes.
 */
@Composable
@ReadOnlyComposable
fun TypographyVariant.style(): TextStyle {
    val type = MaterialTheme.typography
    return when (this) {
        TypographyVariant.DisplayLarge -> {
            type.displayLarge
        }

        TypographyVariant.DisplayMedium -> {
            type.displayMedium
        }

        TypographyVariant.DisplaySmall -> {
            type.displaySmall
        }

        TypographyVariant.HeadlineLarge -> {
            type.headlineLarge
        }

        TypographyVariant.HeadlineMedium -> {
            type.headlineMedium
        }

        TypographyVariant.HeadlineSmall -> {
            type.headlineSmall
        }

        TypographyVariant.TitleLarge -> {
            type.titleLarge
        }

        TypographyVariant.TitleMedium -> {
            type.titleMedium
        }

        TypographyVariant.TitleSmall -> {
            type.titleSmall
        }

        TypographyVariant.BodyLarge -> {
            type.bodyLarge
        }

        TypographyVariant.BodyMedium -> {
            type.bodyMedium
        }

        TypographyVariant.BodySmall -> {
            type.bodySmall
        }

        TypographyVariant.LabelLarge -> {
            type.labelLarge
        }

        TypographyVariant.LabelMedium -> {
            type.labelMedium
        }

        TypographyVariant.LabelSmall -> {
            type.labelSmall
        }

        TypographyVariant.Overline -> {
            type.labelSmall.copy(letterSpacing = type.labelSmall.fontSize * 0.1f)
        }

        TypographyVariant.Caption -> {
            type.bodySmall
        }

        TypographyVariant.Metric -> {
            type.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
        }

        TypographyVariant.Code -> {
            type.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }

        TypographyVariant.Link -> {
            type.bodyMedium.copy(textDecoration = TextDecoration.Underline)
        }
    }
}
