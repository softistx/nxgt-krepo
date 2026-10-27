package com.softistx.material.foundation.motion

import java.util.concurrent.TimeUnit

/**
 * Where each desktop OS keeps the two settings, and how to ask for them without a native library.
 *
 * - **macOS**: `defaults read com.apple.universalaccess reduceMotion` / `increaseContrast`, the
 *   preferences behind `NSWorkspace.accessibilityDisplayShould…`.
 * - **Linux (GNOME)**: `gsettings` `enable-animations` (false means reduced) and `high-contrast`.
 *   Another desktop answers nothing, which reads as "no preference".
 * - **Windows**: high contrast from the `HighContrast\Flags` registry value. Windows keeps "Animation
 *   effects" in a bit mask with no command that reads it, so reduce motion is **not read there**:
 *   set `-Dstx.material.reduceMotion=true`.
 */
internal object DesktopPreferences {
    private const val MAC = "com.apple.universalaccess"
    private const val GNOME = "org.gnome.desktop"
    private const val WIN_CONTRAST = "HKCU\\Control Panel\\Accessibility\\HighContrast"

    /** (reduce motion, increase contrast) as the OS answers, asked once per process. */
    private val os by lazy {
        val osName = System.getProperty("os.name").orEmpty()
        query(reduceMotion = true, osName, ::run) to query(reduceMotion = false, osName, ::run)
    }

    fun reduceMotion(): Boolean = override(REDUCE_MOTION_PROPERTY) ?: os.first

    fun increaseContrast(): Boolean = override(INCREASE_CONTRAST_PROPERTY) ?: os.second

    /** A hand-set property, or null when there is none. Read on every call so a spec can set and clear it. */
    fun override(property: String): Boolean? = System.getProperty(property)?.trim()?.lowercase()?.toBooleanStrictOrNull()

    /** What [osName] says about one setting (reduce motion, or else increase contrast), via [run]; false when it answers nothing. */
    fun query(
        reduceMotion: Boolean,
        osName: String,
        run: (List<String>) -> String?,
    ): Boolean {
        val name = osName.lowercase()
        return when {
            "mac" in name -> {
                val key = if (reduceMotion) "reduceMotion" else "increaseContrast"
                run(listOf("defaults", "read", MAC, key))?.trim() == "1"
            }
            "windows" in name -> !reduceMotion && windowsContrast(run)
            reduceMotion -> run(listOf("gsettings", "get", "$GNOME.interface", "enable-animations"))?.trim() == "false"
            else -> run(listOf("gsettings", "get", "$GNOME.a11y.interface", "high-contrast"))?.trim() == "true"
        }
    }

    private fun windowsContrast(run: (List<String>) -> String?): Boolean {
        val flags = run(listOf("reg", "query", WIN_CONTRAST, "/v", "Flags"))?.trim()?.substringAfterLast(' ')?.toIntOrNull()
        return flags != null && flags and HIGH_CONTRAST_ON != 0
    }

    private fun run(command: List<String>): String? =
        runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0) {
                process.inputStream.bufferedReader().readText()
            } else {
                null
            }
        }.getOrNull()

    private const val HIGH_CONTRAST_ON = 1
    private const val COMMAND_TIMEOUT_SECONDS = 2L
}
