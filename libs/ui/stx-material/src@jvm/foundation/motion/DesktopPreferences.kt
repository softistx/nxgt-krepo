package com.softistx.material.foundation.motion

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The system property that sets reduce motion by hand: the only thing the desktop theme reads by default. */
internal const val REDUCE_MOTION_PROPERTY = "stx.material.reduceMotion"
private const val MAC = "com.apple.universalaccess"
private const val GNOME_INTERFACE = "org.gnome.desktop.interface"
private const val COMMAND_TIMEOUT_MILLIS = 2_000L

/**
 * Asks the desktop OS whether the user wants reduced motion, for an app that opts in. Nothing in the
 * theme calls this: the theme reads only `-Dstx.material.reduceMotion`. It runs on [Dispatchers.IO]
 * and does not block the caller.
 *
 * - **macOS**: `defaults read com.apple.universalaccess reduceMotion`, the preference behind
 *   `NSWorkspace.accessibilityDisplayShouldReduceMotion` (not reachable from the JVM without JNA).
 * - **Linux (GNOME)**: `gsettings get org.gnome.desktop.interface enable-animations`.
 * - **Windows**: "Animation effects" lives in a bit mask no command reads, so the answer is `null`.
 *
 * `null` means unknown: another desktop, a command that is missing, fails or takes over two seconds.
 * Pass the answer in, it is not installed for you:
 *
 * ```kotlin
 * var reduced by remember { mutableStateOf(false) }
 * LaunchedEffect(Unit) { reduced = readDesktopReducedMotion() == true }
 * StxTheme(motion = StxMotion.system(reduced)) { App() }
 * ```
 */
suspend fun readDesktopReducedMotion(): Boolean? = DesktopPreferences.reducedMotion(System.getProperty("os.name").orEmpty())

internal object DesktopPreferences {
    /** A hand-set property, or null when there is none or it is not `true` or `false`. */
    fun override(property: String): Boolean? =
        System
            .getProperty(property)
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull()

    /** What [osName] says about reduce motion, through [runner] (a spec passes one to count calls and fake the output); null when unknown. */
    suspend fun reducedMotion(
        osName: String,
        runner: suspend (List<String>) -> String? = ::runCommand,
    ): Boolean? {
        val name = osName.lowercase()
        return when {
            "mac" in name -> {
                runner(listOf("defaults", "read", MAC, "reduceMotion"))?.trim()?.let { it == "1" }
            }

            "windows" in name -> {
                null
            }

            else -> {
                when (runner(listOf("gsettings", "get", GNOME_INTERFACE, "enable-animations"))?.trim()) {
                    "false" -> true
                    "true" -> false
                    else -> null
                }
            }
        }
    }
}

/**
 * Runs [command] and answers its output, or null when it fails, is missing or outlives [timeoutMillis].
 *
 * The output is read before the exit is awaited, so a full pipe cannot deadlock the child; a watchdog
 * destroys the process at the timeout or when the caller is cancelled, which ends the read; and the process is always reaped.
 */
internal suspend fun runCommand(
    command: List<String>,
    timeoutMillis: Long = COMMAND_TIMEOUT_MILLIS,
    start: (List<String>) -> Process = { ProcessBuilder(it).redirectErrorStream(true).start() },
): String? =
    withContext(Dispatchers.IO) {
        val process = runCatching { start(command) }.getOrNull() ?: return@withContext null
        var timedOut = false
        var text: String? = null
        var finished = false
        try {
            coroutineScope {
                val watchdog =
                    launch(start = CoroutineStart.ATOMIC) {
                        try {
                            delay(timeoutMillis)
                            timedOut = true
                        } finally {
                            if (!finished) process.destroyForcibly()
                        }
                    }
                text = runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrNull()
                finished = true
                watchdog.cancel()
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
            process.waitFor()
        }
        if (timedOut || process.exitValue() != 0) null else text
    }
