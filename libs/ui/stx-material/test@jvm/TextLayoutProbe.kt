package com.softistx.material

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult

/** The layout a `Text` node was measured with (line count, overflow, style), or null for a node with no text. */
fun SemanticsNode.textLayout(): TextLayoutResult? {
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.firstOrNull()
}

/** [textLayout] of the node this interaction finds. */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult? = fetchSemanticsNode().textLayout()
