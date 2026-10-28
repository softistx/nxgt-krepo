package com.softistx.material.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonSize
import com.softistx.material.components.content.Card
import com.softistx.material.foundation.motion.animateStagger
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/**
 * Renders a [UiState]: the loading face while it loads, [content] once it has data, and an error
 * [Alert] with a retry button when it failed. A screen writes only its loaded face.
 *
 * ```kotlin
 * StateHost(state = state, onRetry = viewModel::reload) { rows -> Table(rows) }
 * ```
 *
 * The loading face defaults to [SkeletonCards]; pass [loading] for one shaped like the screen. It is
 * announced as [loadingLabel], and the error face as a polite live region, so a screen reader hears
 * the change. The strings are parameters so an app can translate them. Without `style`: this is a
 * host, the look lives in the [Alert], the [Button] and the [Card]s it composes.
 */
@Composable
fun <T> StateHost(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    errorTitle: String = "Could not load",
    retryLabel: String = "Retry",
    loadingLabel: String = "Loading",
    loading: @Composable () -> Unit = { SkeletonCards() },
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading ->
            Column(modifier.semantics { contentDescription = loadingLabel }) { loading() }
        is UiState.Error ->
            Alert(
                text = state.message,
                tone = Tone.Error,
                title = errorTitle,
                modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                action = { Button(text = retryLabel, onClick = onRetry, size = ButtonSize.Medium) },
            )
        is UiState.Data -> content(state.data)
    }
}

/**
 * [count] card-shaped placeholders that shimmer while the data is on its way, arriving one after the
 * other. With reduced motion they appear at once and do not shimmer.
 */
@Composable
fun SkeletonCards(
    modifier: Modifier = Modifier,
    count: Int = 4,
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
