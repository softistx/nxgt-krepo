package com.softistx.material.foundation.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.softistx.material.foundation.theme.StxTheme

/**
 * A [Shape] that is [target] and, when [target] changes, morphs from the previous polygon to the
 * new one on `motion.spatial`.
 *
 * The spring overshoots, so for a moment the outline goes past the new shape and settles back:
 * that is the expressive give, and a polygon morph tolerates it. With motion disabled the spec is a
 * snap and the shape just changes. A change that arrives mid-morph restarts from the shape it was
 * heading to, not from where it had got to.
 */
@Composable
internal fun rememberMorphShape(
    target: RoundedPolygon,
    speed: MotionSpeed = MotionSpeed.Default,
): Shape {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val spec = StxTheme.motion.spatial<Float>(speed)
    LaunchedEffect(target) {
        if (target !== to) {
            from = to
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        }
    }
    return remember(from, to) { MorphShape(Morph(from, to)) { progress.value } }
}

private class MorphShape(
    private val morph: Morph,
    private val progress: () -> Float,
) : Shape {
    private val path = Path()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        path.rewind()
        morph.toPath(progress(), path)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}
