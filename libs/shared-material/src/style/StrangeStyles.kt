package com.strange.material.style

import androidx.compose.foundation.style.Style
import com.strange.material.button.buttonStyle
import com.strange.material.display.alertStyle
import com.strange.material.display.cardStyle
import com.strange.material.display.chipStyle
import com.strange.material.display.listTileStyle
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/**
 * Every style this library dresses a component with, in one place.
 *
 * There is less here than there once was, and that is the point: colour, shape, border, padding and
 * elevation moved into Material 3's own `*Colors` and `*Defaults` when the components were rebuilt
 * on M3. What is left is what M3 has no parameter for — the press scale, the disabled alpha, the
 * alert's whole appearance because M3 has no banner.
 *
 * A component already applies its own, so nothing here is needed to *use* the library. It is the
 * seam for adding to one: `style = StrangeTheme.styles.card then { alpha(0.6f) }` restates the
 * default and edits it. To change a *colour*, pass Material 3's `*Colors` instead — a `background`
 * in a style block paints over the component M3 already painted.
 */
object StrangeStyles {
    val button: Style get() = buttonStyle

    val card: Style get() = cardStyle

    val chip: Style get() = chipStyle

    val listTile: Style get() = listTileStyle

    fun alert(tone: Tone = Tone.Info): Style = alertStyle(tone)
}

/**
 * `StrangeTheme.styles`, following the Styles API's own advice: the styles are a static reference,
 * not a `CompositionLocal`, because a `Style` reads its tokens when it is applied rather than when
 * it is written. The extension lives here so `theme` keeps knowing nothing about the components.
 */
val StrangeTheme.styles: StrangeStyles get() = StrangeStyles
