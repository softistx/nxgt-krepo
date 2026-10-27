package com.softistx.material.foundation.theme

/**
 * How the screen should pick its scheme.
 *
 * [System] is "follow the platform". [Light] and [Dark] pin it. The catalogue's header already
 * has this switch; [com.softistx.material.components.form.ThemeToggle] is the same choice on a settings
 * screen.
 */
enum class ColorMode {
    Light,
    Dark,
    System,
}
