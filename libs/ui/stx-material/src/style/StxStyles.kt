package com.softistx.material.style

import androidx.compose.foundation.style.Style
import com.softistx.material.button.buttonStyle
import com.softistx.material.display.alertStyle
import com.softistx.material.display.cardStyle
import com.softistx.material.display.chipStyle
import com.softistx.material.display.listTileStyle
import com.softistx.material.form.fieldStyle
import com.softistx.material.navigation.breadcrumbStyle
import com.softistx.material.navigation.navigationItemStyle
import com.softistx.material.navigation.stepperStyle
import com.softistx.material.theme.StxTheme
import com.softistx.material.theme.Tone

/**
 * Every style this library dresses a component with, in one place.
 *
 * There is less here than there once was, and that is the point: colour, shape, border, padding and
 * elevation moved into Material 3's own `*Colors` and `*Defaults` when the components were rebuilt
 * on M3. What is left is what M3 has no parameter for — the press scale, the disabled alpha, the
 * alert's whole appearance because M3 has no banner.
 *
 * A component already applies its own, so nothing here is needed to *use* the library. It is the
 * seam for adding to one: `style = StxTheme.styles.card then { alpha(0.6f) }` restates the
 * default and edits it. To change a *colour*, pass Material 3's `*Colors` instead — a `background`
 * in a style block paints over the component M3 already painted.
 */
object StxStyles {
    val button: Style get() = buttonStyle

    val card: Style get() = cardStyle

    val chip: Style get() = chipStyle

    val field: Style get() = fieldStyle

    val listTile: Style get() = listTileStyle

    val navigationItem: Style get() = navigationItemStyle

    val breadcrumb: Style get() = breadcrumbStyle

    val stepper: Style get() = stepperStyle

    fun alert(tone: Tone = Tone.Info): Style = alertStyle(tone)
}

/**
 * `StxTheme.styles`, following the Styles API's own advice: the styles are a static reference,
 * not a `CompositionLocal`, because a `Style` reads its tokens when it is applied rather than when
 * it is written. The extension lives here so `theme` keeps knowing nothing about the components.
 */
val StxTheme.styles: StxStyles get() = StxStyles
