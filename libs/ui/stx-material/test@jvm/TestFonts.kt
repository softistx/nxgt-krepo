package com.softistx.material

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * A font bundled with the tests, with a real Regular and a real Bold face (Cousine, SIL OFL, under
 * `resources/fonts/`).
 *
 * A pixel spec about glyph ink must not depend on the host's fonts: macOS resolves the default family
 * to a system face with every weight, while a headless Linux runner resolves it to whatever fontconfig
 * has (DejaVu on ubuntu-24.04), where a heavier weight can render identical to the regular one and the
 * glyph shapes differ. With this family the same bytes are drawn on every host.
 *
 * Medium and SemiBold are the Bold face, so a spec on this family can tell regular from heavier, never
 * one heavy weight from another.
 */
object TestFonts {
    val family: FontFamily by lazy {
        FontFamily(
            Font("cousine-regular", bytes("Cousine-Regular.ttf"), FontWeight.Normal),
            // Compose picks the nearest face, and the library raises a regular role to Medium or
            // SemiBold: without these, 500 and 600 would fall back to Regular and look unchanged.
            Font("cousine-medium", bytes("Cousine-Bold.ttf"), FontWeight.Medium),
            Font("cousine-semibold", bytes("Cousine-Bold.ttf"), FontWeight.SemiBold),
            Font("cousine-bold", bytes("Cousine-Bold.ttf"), FontWeight.Bold),
        )
    }

    /** M3's default type scale with [family] on every regular role (the library derives the emphasised weight from them). */
    fun typography(): Typography {
        val base = Typography()

        fun TextStyle.bundled() = copy(fontFamily = family)
        return base.copy(
            displayLarge = base.displayLarge.bundled(),
            displayMedium = base.displayMedium.bundled(),
            displaySmall = base.displaySmall.bundled(),
            headlineLarge = base.headlineLarge.bundled(),
            headlineMedium = base.headlineMedium.bundled(),
            headlineSmall = base.headlineSmall.bundled(),
            titleLarge = base.titleLarge.bundled(),
            titleMedium = base.titleMedium.bundled(),
            titleSmall = base.titleSmall.bundled(),
            bodyLarge = base.bodyLarge.bundled(),
            bodyMedium = base.bodyMedium.bundled(),
            bodySmall = base.bodySmall.bundled(),
            labelLarge = base.labelLarge.bundled(),
            labelMedium = base.labelMedium.bundled(),
            labelSmall = base.labelSmall.bundled(),
        )
    }

    private fun bytes(name: String): ByteArray =
        checkNotNull(TestFonts::class.java.getResourceAsStream("/fonts/$name")) { "missing test font $name" }
            .use { it.readBytes() }
}
