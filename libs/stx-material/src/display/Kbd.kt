package com.softistx.material.display

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * A keyboard shortcut, drawn as keys. Material 3 has no equivalent.
 *
 * Pass the keys in order — `Kbd(listOf("Ctrl", "K"))` — and they sit as separate keycaps with a
 * gap, which is how a shortcut is read. A single string like `"⌘K"` is one keycap, the macOS
 * spelling of the same thing.
 */
@Composable
fun Kbd(
    keys: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { key ->
            Typography(
                text = key,
                variant = TypographyVariant.Code,
                modifier =
                    Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.extraSmall,
                        ).padding(
                            horizontal = StrangeTheme.spacing.xs,
                            vertical = StrangeTheme.spacing.xxs,
                        ),
            )
        }
    }
}
