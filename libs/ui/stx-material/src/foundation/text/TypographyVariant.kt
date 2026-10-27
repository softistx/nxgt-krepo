package com.softistx.material.foundation.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

    /** A quantity meant to be read at a glance: the number on a summary tile. Display size, `SemiBold`. */
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
 *
 * [emphasized] picks the Material 3 `*Emphasized` style of the same role — the expressive type
 * axis, `Medium` (500) weight with the role's size and spacing. It is a different thing from
 * `Emphasis` (see [Typography]'s `emphasis`), which only dims the colour. The five extra variants
 * have no emphasised twin and ignore the flag: `Metric` is already emphasised, at `SemiBold`
 * rather than M3's `Medium`, and heavier still when the theme's `displaySmallEmphasized` is: the
 * figure is never lighter than `SemiBold` nor than the theme.
 */
@Composable
@ReadOnlyComposable
fun TypographyVariant.style(emphasized: Boolean = false): TextStyle {
    val type = MaterialTheme.typography
    return when (this) {
        TypographyVariant.DisplayLarge -> type.displayLarge.weighted(type.displayLargeEmphasized, emphasized)
        TypographyVariant.DisplayMedium -> type.displayMedium.weighted(type.displayMediumEmphasized, emphasized)
        TypographyVariant.DisplaySmall -> type.displaySmall.weighted(type.displaySmallEmphasized, emphasized)
        TypographyVariant.HeadlineLarge -> type.headlineLarge.weighted(type.headlineLargeEmphasized, emphasized)
        TypographyVariant.HeadlineMedium -> type.headlineMedium.weighted(type.headlineMediumEmphasized, emphasized)
        TypographyVariant.HeadlineSmall -> type.headlineSmall.weighted(type.headlineSmallEmphasized, emphasized)
        TypographyVariant.TitleLarge -> type.titleLarge.weighted(type.titleLargeEmphasized, emphasized)
        TypographyVariant.TitleMedium -> type.titleMedium.weighted(type.titleMediumEmphasized, emphasized)
        TypographyVariant.TitleSmall -> type.titleSmall.weighted(type.titleSmallEmphasized, emphasized)
        TypographyVariant.BodyLarge -> type.bodyLarge.weighted(type.bodyLargeEmphasized, emphasized)
        TypographyVariant.BodyMedium -> type.bodyMedium.weighted(type.bodyMediumEmphasized, emphasized)
        TypographyVariant.BodySmall -> type.bodySmall.weighted(type.bodySmallEmphasized, emphasized)
        TypographyVariant.LabelLarge -> type.labelLarge.weighted(type.labelLargeEmphasized, emphasized)
        TypographyVariant.LabelMedium -> type.labelMedium.weighted(type.labelMediumEmphasized, emphasized)
        TypographyVariant.LabelSmall -> type.labelSmall.weighted(type.labelSmallEmphasized, emphasized)
        TypographyVariant.Overline -> overline(type.labelSmall)
        TypographyVariant.Caption -> type.bodySmall
        TypographyVariant.Metric -> type.displaySmallEmphasized.atLeast(FontWeight.SemiBold)
        TypographyVariant.Code -> type.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        TypographyVariant.Link -> type.bodyMedium.copy(textDecoration = TextDecoration.Underline)
    }
}

/**
 * [emphasised] when asked for, with its weight guaranteed to be heavier than this role's own.
 *
 * Material 3 documents its `*Emphasized` styles as `Medium` (500), but in 1.12.0-alpha03 a default
 * `Typography()` hands back the regular weight for most roles (verified: `headlineMedium` and
 * `headlineMediumEmphasized` are both 400), which would make the flag a no-op. M3's size, line height
 * and family are kept; the weight is raised to `Medium`, or to `SemiBold` for the roles that are
 * already `Medium`, whenever M3's own value is not heavier. A theme that supplies real emphasised
 * styles keeps them.
 */
private fun TextStyle.weighted(
    emphasised: TextStyle,
    emphasized: Boolean,
): TextStyle {
    if (!emphasized) return this
    val own = (fontWeight ?: FontWeight.Normal).weight
    val theirs = (emphasised.fontWeight ?: FontWeight.Normal).weight
    val floor = if (own < FontWeight.Medium.weight) FontWeight.Medium else FontWeight.SemiBold
    return if (theirs > own) emphasised else emphasised.copy(fontWeight = floor)
}

private fun TextStyle.atLeast(floor: FontWeight): TextStyle =
    if ((fontWeight ?: FontWeight.Normal).weight >= floor.weight) this else copy(fontWeight = floor)

private fun overline(base: TextStyle): TextStyle = base.copy(letterSpacing = base.fontSize * 0.1f)
