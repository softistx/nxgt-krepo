package com.strange.material.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.button.ButtonColor
import com.strange.material.button.ButtonVariant
import com.strange.material.button.IconButton
import com.strange.material.icon.StrangeIcons
import com.strange.material.motion.Transitions
import kotlinx.coroutines.launch

/**
 * A round button that returns a list to the top, only once the reader has actually left it.
 *
 * Sit it in a `Box` over the list. It uses [LazyListState.firstVisibleItemIndex], so it does not
 * guess from a pixel offset that a snap or a header would lie about.
 */
@Composable
fun BoxScope.ScrollToTop(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    after: Int = 2,
) {
    val visible by remember(listState, after) {
        derivedStateOf { listState.firstVisibleItemIndex >= after }
    }
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.align(Alignment.BottomEnd),
        enter = Transitions.fade,
        exit = Transitions.fadeAway,
    ) {
        IconButton(
            icon = StrangeIcons.ChevronUp,
            description = "Back to top",
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            variant = ButtonVariant.Filled,
            color = ButtonColor.Primary,
        )
    }
}
