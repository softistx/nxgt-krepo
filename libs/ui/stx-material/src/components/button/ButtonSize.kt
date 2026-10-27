package com.softistx.material.components.button

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.Dp

/**
 * How big a [Button] is, from Material 3 Expressive's five sizes.
 *
 * A size is a height; the padding, the icon, the label style and the pressed corner all follow
 * from it through `ButtonDefaults.*For(height)`, so the five stay in step with M3.
 * [Small] is the long-standing 40 dp button and the default.
 */
enum class ButtonSize {
    /** 32 dp. Dense toolbars and table rows. */
    ExtraSmall,

    /** 40 dp. The default. */
    Small,

    /** 56 dp. A primary action in a form or on an empty state. */
    Medium,

    /** 96 dp. A hero action. */
    Large,

    /** 136 dp. The one thing on the screen. */
    ExtraLarge,
    ;

    /** The container height M3 gives this size. */
    val height: Dp
        get() =
            when (this) {
                ExtraSmall -> ButtonDefaults.ExtraSmallContainerHeight
                Small -> ButtonDefaults.MinHeight
                Medium -> ButtonDefaults.MediumContainerHeight
                Large -> ButtonDefaults.LargeContainerHeight
                ExtraLarge -> ButtonDefaults.ExtraLargeContainerHeight
            }
}
