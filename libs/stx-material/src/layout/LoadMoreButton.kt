package com.strange.material.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.button.Button
import com.strange.material.button.ButtonVariant
import com.strange.material.feedback.Progress
import com.strange.material.feedback.ProgressKind

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
