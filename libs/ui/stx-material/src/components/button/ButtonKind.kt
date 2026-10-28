package com.softistx.material.components.button

/**
 * The four surfaces Material 3 can actually draw for a [ButtonVariant].
 *
 * [ButtonVariant.Link] is [ButtonVariant.Ghost] on every control but the text [Button], which
 * alone underlines it; M3 has no separate composable, colour factory or border for it. Each
 * control of the family picks its M3 composable and colours by `when (variant.kind)`, so a new
 * variant is placed here once and the compiler then points at any control that cannot draw it.
 */
internal enum class ButtonKind {
    Filled,
    Tonal,
    Outlined,
    Quiet,
}

internal val ButtonVariant.kind: ButtonKind
    get() =
        when (this) {
            ButtonVariant.Filled -> ButtonKind.Filled
            ButtonVariant.Tonal -> ButtonKind.Tonal
            ButtonVariant.Outlined -> ButtonKind.Outlined
            ButtonVariant.Ghost, ButtonVariant.Link -> ButtonKind.Quiet
        }
