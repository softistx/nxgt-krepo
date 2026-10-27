package com.softistx.material.components.surface

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.style.Style
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy

/**
 * A sheet that rises from the bottom. Material 3's `ModalBottomSheet`.
 *
 * Not composed when hidden. The drag handle is M3's; the content is the caller's. [style] has no default look: M3 paints
 * the sheet's container over a background.
 */
@Composable
fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.styledBy(style),
        sheetState = rememberModalBottomSheetState(),
        content = content,
    )
}
