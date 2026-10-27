package com.softistx.material.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.motion.rememberMorphShape
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/** The marker's size: wide enough for the scalloped current shape to keep its number inside. */
internal val StepMarkerSize = 32.dp

/**
 * The shape a step wears: the current one is a `Cookie9Sided`, the done and upcoming ones are
 * circles. When [stepStatus] moves a step on, the marker morphs between the two.
 */
internal fun StepStatus.polygon(): RoundedPolygon = if (this == StepStatus.Current) MaterialShapes.Cookie9Sided else MaterialShapes.Circle

/**
 * The marker of one step and the connector to the next. [vertical] draws the connector down,
 * otherwise it fills the width to the right.
 */
@Composable
internal fun StepMarker(
    status: StepStatus,
    index: Int,
    connector: Boolean,
    vertical: Boolean,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepPlate(status, index)
            if (connector) Box(Modifier.width(2.dp).height(StxTheme.spacing.lg).background(line))
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StepPlate(status, index)
            if (connector) Box(Modifier.weight(1f).height(2.dp).background(line))
        }
    }
}

/** The plate: a number, or a check once done, on a shape that morphs with the status. */
@Composable
private fun StepPlate(
    status: StepStatus,
    index: Int,
) {
    val scheme = MaterialTheme.colorScheme
    val done = status != StepStatus.Upcoming
    val fill = if (done) scheme.primary else scheme.surfaceContainerHighest
    val content = if (done) scheme.onPrimary else scheme.onSurfaceVariant
    Box(
        modifier = Modifier.size(StepMarkerSize).clip(rememberMorphShape(status.polygon())).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (status == StepStatus.Done) {
            Icon(icon = StxIcons.Check, description = null, size = IconSize.Small, tint = content)
        } else {
            Typography(text = (index + 1).toString(), variant = TypographyVariant.LabelSmall, color = content)
        }
    }
}
