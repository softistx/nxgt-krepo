package com.softistx.material.display

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.button.ButtonColor
import com.softistx.material.button.IconButton
import com.softistx.material.icon.IconSize
import com.softistx.material.icon.StrangeIcons

/**
 * A star rating. Material 3 has no rating control.
 *
 * [value] is 1..[max]; 0 is none. Each star is an [IconButton], so the 48 dp target, the ripple
 * and the semantics are M3's rather than a clickable icon. Tapping the current value clears it.
 * Filled stars use the warning colour — the one that already means "attention without alarm".
 */
@Composable
fun Rating(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    max: Int = 5,
    enabled: Boolean = true,
) {
    Row(modifier = modifier) {
        repeat(max) { index ->
            val star = index + 1
            IconButton(
                icon = StrangeIcons.Star,
                description = "$star of $max",
                onClick = { onChange(if (value == star) 0 else star) },
                color = if (star <= value) ButtonColor.Warning else ButtonColor.Neutral,
                size = IconSize.Large,
                enabled = enabled,
            )
        }
    }
}
