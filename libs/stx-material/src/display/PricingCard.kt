package com.strange.material.display

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.Tone

/**
 * A plan on a pricing page: name, [Price], what is included, and the action.
 *
 * [highlighted] lifts the card so the recommended plan is the one that sits above its neighbours.
 * [badge] is usually "Popular" — a [StatusBadge], not a second title. Material 3 has no plan card.
 */
@Composable
fun PricingCard(
    name: String,
    amount: String,
    modifier: Modifier = Modifier,
    compareAt: String? = null,
    period: String? = null,
    description: String? = null,
    features: List<FeatureItem> = emptyList(),
    highlighted: Boolean = false,
    badge: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        variant = if (highlighted) CardVariant.Elevated else CardVariant.Outlined,
    ) {
        if (badge != null) {
            StatusBadge(text = badge, tone = Tone.Info)
        }
        Typography(text = name, variant = TypographyVariant.TitleMedium)
        if (description != null) {
            Typography(text = description, emphasis = Emphasis.Medium)
        }
        Price(amount = amount, compareAt = compareAt, period = period)
        if (features.isNotEmpty()) {
            FeatureList(items = features)
        }
        action?.invoke()
    }
}
