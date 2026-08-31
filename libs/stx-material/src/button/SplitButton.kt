package com.strange.material.button

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.strange.material.icon.Icon
import com.strange.material.icon.StrangeIcons
import com.strange.material.surface.Menu
import com.strange.material.surface.MenuItem
import com.strange.material.text.Typography

/**
 * A primary action with a sibling that opens alternatives. Material 3's `SplitButtonLayout`.
 *
 * The leading half is [text]; the trailing half is a chevron that opens [overflow]. Two actions
 * that look connected because they are — a Save plus a Save as, not two buttons in a row.
 */
@Composable
fun SplitButton(
    text: String,
    onClick: () -> Unit,
    overflow: List<MenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = onClick, enabled = enabled) {
                    Typography(text = text)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    onClick = { open = true },
                    enabled = enabled && overflow.isNotEmpty(),
                ) {
                    Icon(icon = StrangeIcons.ChevronDown, description = "More actions")
                }
            },
        )
        Menu(expanded = open, onDismiss = { open = false }, items = overflow)
    }
}
