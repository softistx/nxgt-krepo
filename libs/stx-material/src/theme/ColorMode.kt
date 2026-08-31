package com.strange.material.theme

/**
 * How the screen should pick its scheme.
 *
 * [System] is "follow the platform". [Light] and [Dark] pin it. The catalogue's header already
 * has this switch; [com.strange.material.form.ThemeToggle] is the same choice on a settings
 * screen.
 */
enum class ColorMode {
    Light,
    Dark,
    System,
}
