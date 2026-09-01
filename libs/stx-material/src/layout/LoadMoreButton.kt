package com.softistx.material.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonVariant
import com.softistx.material.feedback.Progress
import com.softistx.material.feedback.ProgressKind

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
    label: String = "Load more",
) {
    if (!hasMore) return
    if (loading) {
        Progress(modifier = modifier, kind = ProgressKind.Circular)
    } else {
        Button(text = label, onClick = onClick, modifier = modifier, variant = ButtonVariant.Ghost)
    }
}
