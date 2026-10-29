package com.softistx.material.components.layout

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.feedback.Progress
import com.softistx.material.components.feedback.ProgressKind
import com.softistx.material.foundation.theme.LocalStxStrings

/**
 * The next page of a keyset, as a control rather than an infinite scroll.
 *
 * Hidden when there is nothing more. [loading] replaces the label with a spinner, so the reader
 * cannot tap twice while a page is in flight.
 */
@Composable
fun LoadMoreButton(
    hasMore: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = LocalStxStrings.current.loadMore,
    style: Style = Style,
) {
    if (!hasMore) return
    if (loading) {
        Progress(modifier = modifier, kind = ProgressKind.Circular)
    } else {
        Button(text = label, onClick = onClick, modifier = modifier, variant = ButtonVariant.Ghost, style = style)
    }
}
