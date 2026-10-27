package com.softistx.material.school.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.content.Card
import com.softistx.material.components.feedback.Alert
import com.softistx.material.components.feedback.Skeleton
import com.softistx.material.components.feedback.UiState
import com.softistx.material.foundation.motion.animateStagger
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/**
 * Loading, error or data, decided in one place so a screen writes only its loaded face. The
 * loading face is the screen's own skeleton; the error face is always the same alert with a retry.
 */
@Composable
fun <T> StateHost(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { SkeletonCards() },
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> Column(modifier) { loading() }
        is UiState.Error -> ErrorPanel(state.message, onRetry)
        is UiState.Data -> content(state.data)
    }
}

@Composable
fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Alert(
        text = message,
        tone = Tone.Error,
        title = "Could not load",
        modifier = modifier.fillMaxWidth(),
        action = { Button(text = "Retry", onClick = onRetry) },
    )
}

/** A few card-shaped placeholders that shimmer while the data is on its way. */
@Composable
fun SkeletonCards(
    count: Int = 4,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        repeat(count) { index ->
            Card(modifier = Modifier.fillMaxWidth().animateStagger(index)) {
                Skeleton(height = 18.dp, modifier = Modifier.fillMaxWidth(0.5f))
                Skeleton()
                Skeleton(modifier = Modifier.fillMaxWidth(0.8f))
            }
        }
    }
}
