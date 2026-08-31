package com.strange.material.button

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import com.strange.material.icon.IconSize
import com.strange.material.icon.StrangeIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copy [text] to the clipboard and flash a check. The host never writes the clipboard dance.
 *
 * Writes through `LocalClipboard.setClipEntry`, which is suspend. `ClipEntry` is still native
 * per target, so [plainTextClip] is the common constructor.
 */
@Composable
fun CopyButton(
    text: String,
    modifier: Modifier = Modifier,
    description: String = "Copy",
    variant: ButtonVariant = ButtonVariant.Ghost,
    color: ButtonColor = ButtonColor.Neutral,
    size: IconSize = IconSize.Medium,
    enabled: Boolean = true,
    style: Style = Style,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_HOLD_MS)
            copied = false
        }
    }
    IconButton(
        icon = if (copied) StrangeIcons.Check else StrangeIcons.Copy,
        description = if (copied) "Copied" else description,
        onClick = {
            scope.launch {
                clipboard.setClipEntry(plainTextClip(text))
                copied = true
            }
        },
        modifier = modifier,
        variant = variant,
        color = if (copied) ButtonColor.Success else color,
        size = size,
        enabled = enabled && text.isNotEmpty(),
        style = style,
    )
}

private const val COPIED_HOLD_MS = 1_500L
