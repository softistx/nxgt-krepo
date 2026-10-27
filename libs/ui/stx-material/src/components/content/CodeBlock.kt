package com.softistx.material.components.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.CopyButton
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * A snippet of code, with a copy control. Material 3 has no code block.
 *
 * The text is [TypographyVariant.Code]. [CopyButton] sits in the corner so the snippet and the
 * way to take it away are one control, not a row the caller has to remember.
 */
@Composable
fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    copyable: Boolean = true,
) {
    Card(modifier = modifier, variant = CardVariant.Outlined) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Typography(text = text, variant = TypographyVariant.Code)
            if (copyable) {
                CopyButton(text = text, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
}
