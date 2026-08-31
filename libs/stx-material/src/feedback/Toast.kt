package com.strange.material.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone
import kotlinx.coroutines.delay

/** One toast in a [ToasterState] stack. */
data class ToastMessage(
    val text: String,
    val tone: Tone = Tone.Info,
    val id: String,
)

/**
 * A stack of toasts. Material 3's `SnackbarHost` holds one; this holds several, which is the
 * difference that makes a dashboard's "saved" and "failed" able to coexist.
 */
@Stable
class ToasterState internal constructor(
    internal val messages: SnapshotStateList<ToastMessage>,
) {
    private var nextId = 0

    fun show(
        text: String,
        tone: Tone = Tone.Info,
    ) {
        messages += ToastMessage(text = text, tone = tone, id = (nextId++).toString())
    }

    fun dismiss(id: String) {
        messages.removeAll { it.id == id }
    }
}

@Composable
fun rememberToasterState(): ToasterState = remember { ToasterState(mutableStateListOf()) }

/**
 * Hosts a stack of toasts over [content], bottom-end.
 *
 * Each toast is M3's `Snackbar` painted with a [Tone], so the container, the type and the
 * accessibility role stay M3's. Swipe-to-dismiss is the snackbar's own.
 */
@Composable
fun Toaster(
    state: ToasterState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(StrangeTheme.spacing.md)
                    .widthIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
        ) {
            state.messages.forEach { message ->
                LaunchedEffect(message.id) {
                    delay(4_000)
                    state.dismiss(message.id)
                }
                val role = StrangeTheme.colors.tone(message.tone)
                Snackbar(
                    action = null,
                    containerColor = role.container,
                    contentColor = role.onContainer,
                    actionOnNewLine = false,
                    shape = SnackbarDefaults.shape,
                ) {
                    Typography(text = message.text)
                }
            }
        }
    }
}
