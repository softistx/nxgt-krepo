package com.strange.material.style

import androidx.compose.foundation.style.Style
import com.strange.material.button.ButtonColor
import com.strange.material.button.ButtonVariant
import com.strange.material.button.buttonStyle
import com.strange.material.button.iconButtonStyle
import com.strange.material.display.CardVariant
import com.strange.material.display.alertStyle
import com.strange.material.display.badgeStyle
import com.strange.material.display.cardStyle
import com.strange.material.display.chipStyle
import com.strange.material.display.listTileStyle
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/**
 * Every style this library dresses a component with, in one place.
 *
 * A component already applies its own default, so nothing here is needed to *use* the library. It
 * is the seam for changing one: `style = StrangeTheme.styles.card(CardVariant.Elevated) then {
 * border(2.dp, scheme.primary) }` restates the default and edits it, instead of rebuilding a card's
 * appearance from nothing and drifting from the rest of the screen.
 */
object StrangeStyles {
    fun button(
        variant: ButtonVariant = ButtonVariant.Filled,
        color: ButtonColor = ButtonColor.Primary,
    ): Style = buttonStyle(variant, color)

    fun iconButton(
        variant: ButtonVariant = ButtonVariant.Ghost,
        color: ButtonColor = ButtonColor.Neutral,
    ): Style = iconButtonStyle(variant, color)

    fun card(
        variant: CardVariant = CardVariant.Filled,
        interactive: Boolean = false,
    ): Style = cardStyle(variant, interactive)

    fun listTile(interactive: Boolean = false): Style = listTileStyle(interactive)

    fun badge(tone: Tone = Tone.Info): Style = badgeStyle(tone)

    fun alert(tone: Tone = Tone.Info): Style = alertStyle(tone)

    val chip: Style get() = chipStyle
}

/**
 * `StrangeTheme.styles`, following the Styles API's own advice: the styles are a static reference,
 * not a `CompositionLocal`, because a `Style` reads its tokens when it is applied rather than when
 * it is written. The extension lives here so `theme` keeps knowing nothing about the components.
 */
val StrangeTheme.styles: StrangeStyles get() = StrangeStyles
