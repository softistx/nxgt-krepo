package com.softistx.material.foundation.motion

import androidx.compose.animation.core.SnapSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.StxThemeProvider
import com.softistx.material.renderProbe
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** What the theme installed: its [StxMotion] and the scheme M3 sees. */
private class Installed(
    val motion: StxMotion,
    val scheme: MotionScheme,
)

/** Composes [theme] with the OS reduce-motion property set to [os] meanwhile, and answers what it installed. */
private fun installed(
    os: Boolean,
    theme: @Composable (@Composable () -> Unit) -> Unit,
): Installed {
    val before = System.getProperty(REDUCE_MOTION_PROPERTY)
    try {
        System.setProperty(REDUCE_MOTION_PROPERTY, os.toString())
        var motion: StxMotion? = null
        var scheme: MotionScheme? = null
        renderProbe(width = 20, height = 20, content = {
            theme {
                motion = StxTheme.motion
                scheme = MaterialTheme.motionScheme
            }
        }) { settle() }
        return Installed(checkNotNull(motion), checkNotNull(scheme))
    } finally {
        if (before == null) System.clearProperty(REDUCE_MOTION_PROPERTY) else System.setProperty(REDUCE_MOTION_PROPERTY, before)
    }
}

private fun Installed.snaps(): Boolean =
    motion.spatial<Float>() is SnapSpec<*> && motion.effects<Float>() is SnapSpec<*> && scheme.fastSpatialSpec<Float>() is SnapSpec<*>

private fun Installed.runs(): Boolean =
    motion.enabled && motion.spatial<Float>() !is SnapSpec<*> && scheme.fastSpatialSpec<Float>() !is SnapSpec<*>

/** The OS setting reaches the theme only when the caller passes neither a `motionScheme` nor a `motion`. */
class SystemAccessibilityTest :
    FeatureSpec({
        feature("a theme with neither motionScheme nor motion follows the OS") {
            scenario("reduce motion on: StxMotion is disabled and every spec, M3's included, snaps") {
                val theme = installed(os = true) { StxTheme { it() } }
                theme.motion.enabled shouldBe false
                theme.snaps() shouldBe true
            }

            scenario("reduce motion off: motion runs and springs are real") {
                installed(os = false) { StxTheme { it() } }.runs() shouldBe true
            }
        }

        feature("passing either one opts out of the OS read") {
            scenario("only motionScheme: the OS is not read, so motion stays on with the caller's scheme") {
                val theme = installed(os = true) { StxTheme(motionScheme = MotionScheme.standard()) { it() } }
                theme.motion.enabled shouldBe true
                theme.runs() shouldBe true
            }

            scenario("only motion: the OS is not read, so M3 keeps its expressive scheme and the caller's motion is as given") {
                val on = installed(os = true) { StxTheme(motion = StxMotion(enabled = true)) { it() } }
                on.runs() shouldBe true
                val off = installed(os = false) { StxTheme(motion = StxMotion(enabled = false)) { it() } }
                off.motion.enabled shouldBe false
                (off.scheme.fastSpatialSpec<Float>() is SnapSpec<*>) shouldBe false
            }

            scenario("both: the caller's pair is installed untouched, whatever the OS says") {
                val standard = MotionScheme.standard()
                installed(os = true) { StxTheme(motionScheme = standard, motion = StxMotion(enabled = true)) { it() } }.runs() shouldBe true
                installed(os = false) { StxTheme(motionScheme = standard, motion = StxMotion(enabled = false)) { it() } }
                    .motion.enabled shouldBe false
            }
        }

        feature("StxThemeProvider forwards the same rule") {
            scenario("with no motionScheme it follows the OS") {
                installed(os = true) { StxThemeProvider(dynamicColor = false) { it() } }.snaps() shouldBe true
            }
        }

        feature("the desktop theme default reads only the property") {
            scenario("with the property absent it is not reduced, and no command runs") {
                val calls = mutableListOf<List<String>>()
                val before = DesktopPreferences.runner
                val beforeProperty = System.getProperty(REDUCE_MOTION_PROPERTY)
                try {
                    DesktopPreferences.runner = {
                        calls += it
                        "1"
                    }
                    System.clearProperty(REDUCE_MOTION_PROPERTY)
                    var motion: StxMotion? = null
                    renderProbe(width = 20, height = 20, content = { StxTheme { motion = StxTheme.motion } }) { settle() }
                    checkNotNull(motion).enabled shouldBe true
                    calls shouldBe emptyList()
                    readDesktopReducedMotion()
                    calls.size shouldBe 1
                } finally {
                    DesktopPreferences.runner = before
                    if (beforeProperty != null) System.setProperty(REDUCE_MOTION_PROPERTY, beforeProperty)
                }
            }

            scenario("the property wins both ways, and an unparsable value is ignored") {
                val before = System.getProperty(REDUCE_MOTION_PROPERTY)
                try {
                    System.setProperty(REDUCE_MOTION_PROPERTY, "true")
                    DesktopPreferences.override(REDUCE_MOTION_PROPERTY) shouldBe true
                    System.setProperty(REDUCE_MOTION_PROPERTY, "false")
                    DesktopPreferences.override(REDUCE_MOTION_PROPERTY) shouldBe false
                    System.setProperty(REDUCE_MOTION_PROPERTY, "maybe")
                    DesktopPreferences.override(REDUCE_MOTION_PROPERTY) shouldBe null
                } finally {
                    if (before == null) System.clearProperty(REDUCE_MOTION_PROPERTY) else System.setProperty(REDUCE_MOTION_PROPERTY, before)
                }
            }
        }

        feature("readDesktopReducedMotion, the opt-in") {
            suspend fun ask(
                os: String,
                output: String?,
            ): Pair<Boolean?, List<String>> {
                val before = DesktopPreferences.runner
                val asked = mutableListOf<String>()
                try {
                    DesktopPreferences.runner = {
                        asked += it.joinToString(" ")
                        output
                    }
                    return DesktopPreferences.reducedMotion(os) to asked
                } finally {
                    DesktopPreferences.runner = before
                }
            }

            scenario("macOS asks defaults for reduceMotion and reads 1 as on, 0 as off") {
                val (on, asked) = ask("Mac OS X", "1\n")
                on shouldBe true
                asked.single() shouldBe "defaults read com.apple.universalaccess reduceMotion"
                ask("Mac OS X", "0\n").first shouldBe false
            }

            scenario("Linux reads gsettings, where animations off means reduced") {
                ask("Linux", "false\n").first shouldBe true
                ask("Linux", "true\n").first shouldBe false
            }

            scenario("an answer it cannot read, or no answer, is unknown, not false") {
                ask("Linux", "'weird'").first shouldBe null
                ask("Linux", null).first shouldBe null
                ask("Mac OS X", null).first shouldBe null
            }

            scenario("Windows is unknown and no command runs") {
                val (answer, asked) = ask("Windows 11", "1")
                answer shouldBe null
                asked shouldBe emptyList()
            }
        }

        feature("the command runner") {
            scenario("a process that outlives the timeout is destroyed and answers null") {
                val process = HangingProcess()
                runBlocking { runCommand(listOf("hang"), timeoutMillis = 50) { process } } shouldBe null
                process.destroyed shouldBe true
                process.waited shouldBe true
            }

            scenario("a process is destroyed when its caller is cancelled during the read") {
                val process = HangingProcess()
                // A standalone scope, so a missing destroy fails the join below instead of hanging runBlocking.
                runBlocking {
                    val job = CoroutineScope(Dispatchers.Default).launch { runCommand(listOf("hang"), timeoutMillis = 60_000) { process } }
                    process.readStarted.await(5, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
                    job.cancel()
                    withTimeout(5_000) { job.join() }
                }
                process.destroyed shouldBe true
            }

            scenario("output is read, and a failing exit answers null") {
                runBlocking { runCommand(listOf("ok")) { FinishedProcess("1\n", 0) } } shouldBe "1\n"
                runBlocking { runCommand(listOf("bad")) { FinishedProcess("1\n", 1) } } shouldBe null
            }

            scenario("a command that cannot start answers null") {
                runBlocking { runCommand(listOf("missing")) { throw java.io.IOException("no such command") } } shouldBe null
            }
        }
    })

/** A process whose output never ends until it is destroyed, as a hung command would. */
private class HangingProcess : Process() {
    private val gate = java.util.concurrent.CountDownLatch(1)

    @Volatile var destroyed = false

    @Volatile var waited = false

    val readStarted = java.util.concurrent.CountDownLatch(1)

    override fun getInputStream(): java.io.InputStream =
        object : java.io.InputStream() {
            override fun read(): Int {
                readStarted.countDown()
                gate.await()
                return -1
            }
        }

    override fun destroyForcibly(): Process {
        destroyed = true
        gate.countDown()
        return this
    }

    override fun isAlive(): Boolean = !destroyed

    override fun waitFor(): Int {
        waited = true
        return 137
    }

    override fun exitValue(): Int = if (destroyed) 137 else throw IllegalThreadStateException()

    override fun getOutputStream(): java.io.OutputStream = java.io.OutputStream.nullOutputStream()

    override fun getErrorStream(): java.io.InputStream = java.io.InputStream.nullInputStream()

    override fun destroy() {
        destroyForcibly()
    }
}

/** A process that has already finished with [output] and [code]. */
private class FinishedProcess(
    output: String,
    private val code: Int,
) : Process() {
    private val stream = output.byteInputStream()

    override fun getInputStream(): java.io.InputStream = stream

    override fun getOutputStream(): java.io.OutputStream = java.io.OutputStream.nullOutputStream()

    override fun getErrorStream(): java.io.InputStream = java.io.InputStream.nullInputStream()

    override fun waitFor(): Int = code

    override fun exitValue(): Int = code

    override fun isAlive(): Boolean = false

    override fun destroy() = Unit
}
