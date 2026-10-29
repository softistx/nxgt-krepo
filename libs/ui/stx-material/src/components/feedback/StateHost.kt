package com.softistx.material.components.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonSize
import com.softistx.material.foundation.theme.LocalStxStrings
import com.softistx.material.foundation.theme.Tone

/**
 * Renders a [UiState]: the loading face while it loads, [content] once it has data, and an error
 * [Alert] with a retry button when it failed. A screen writes only its loaded face.
 *
 * ```kotlin
 * StateHost(state = state, onRetry = viewModel::reload) { rows -> Table(rows) }
 * ```
 *
 * The loading face defaults to [SkeletonCards]; pass [loading] for one shaped like the screen. The
 * loading face is announced as [loadingLabel]; the loading and error faces are polite live regions.
 * All three faces sit in a [Box] with [modifier], so the data face is laid out within the bounds
 * the caller gave. The strings are parameters so an app can translate them. Without `style`: this
 * is a host, the look lives in the [Alert], the [Button] and the [Card]s it composes.
 */
@Composable
fun <T> StateHost(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    errorTitle: String = LocalStxStrings.current.loadFailed,
    retryLabel: String = LocalStxStrings.current.retry,
    loadingLabel: String = LocalStxStrings.current.loading,
    loading: @Composable () -> Unit = { SkeletonCards() },
    content: @Composable (T) -> Unit,
) {
    Box(modifier) {
        when (state) {
            UiState.Loading -> {
                Column(
                    Modifier.semantics {
                        contentDescription = loadingLabel
                        liveRegion = LiveRegionMode.Polite
                    },
                ) { loading() }
            }

            is UiState.Error -> {
                Alert(
                    text = state.message,
                    tone = Tone.Error,
                    title = errorTitle,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    action = { Button(text = retryLabel, onClick = onRetry, size = ButtonSize.Medium) },
                )
            }

            is UiState.Data -> {
                content(state.data)
            }
        }
    }
}
