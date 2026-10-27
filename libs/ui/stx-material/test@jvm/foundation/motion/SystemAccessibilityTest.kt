package com.softistx.material.foundation.motion

import androidx.compose.animation.core.SnapSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.renderProbe
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Composes [content] under the theme and answers the motion it installed, with [property] set to [value] meanwhile. */
private fun themed(
    property: String,
    value: String?,
    content: @Composable (@Composable () -> Unit) -> Unit = { it() },
): StxMotion {
    val before = System.getProperty(property)
    try {
        if (value == null) System.clearProperty(property) else System.setProperty(property, value)
        var seen: StxMotion? = null
        renderProbe(width = 20, height = 20, content = { content { seen = StxTheme.motion } }) { settle() }
        return checkNotNull(seen)
    } finally {
        if (before == null) System.clearProperty(property) else System.setProperty(property, before)
    }
}

/** The OS settings reach `StxTheme.motion`, and a caller's own motion still wins. */
class SystemAccessibilityTest :
    FeatureSpec({
        feature("reduce motion") {
            scenario("the system setting turns the default theme's motion off, so every spec snaps") {
                val motion = themed(REDUCE_MOTION_PROPERTY, "true") { StxTheme { it() } }
                motion.enabled shouldBe false
                motion.spatial<Float>().shouldBeInstanceOf<SnapSpec<Float>>()
                motion.effects<Float>().shouldBeInstanceOf<SnapSpec<Float>>()
            }

            scenario("the system setting also snaps M3's own scheme, which enabled = false alone leaves running") {
                var scheme: MotionScheme? = null
                themed(REDUCE_MOTION_PROPERTY, "true") {
                    StxTheme {
                        scheme = MaterialTheme.motionScheme
                        it()
                    }
                }
                checkNotNull(scheme).fastSpatialSpec<Float>().shouldBeInstanceOf<SnapSpec<Float>>()
            }

            scenario("without the setting, motion is on and springs are real") {
                val motion = themed(REDUCE_MOTION_PROPERTY, "false") { StxTheme { it() } }
                motion.enabled shouldBe true
                (motion.spatial<Float>() is SnapSpec<*>) shouldBe false
            }

            scenario("an explicit StxMotion wins over the system setting, both ways") {
                themed(REDUCE_MOTION_PROPERTY, "true") { StxTheme(motion = StxMotion(enabled = true)) { it() } }.enabled shouldBe true
                themed(REDUCE_MOTION_PROPERTY, "false") { StxTheme(motion = StxMotion(enabled = false)) { it() } }.enabled shouldBe false
            }
        }

        feature("increase contrast") {
            scenario("the system setting reaches StxTheme.motion, and is off by default") {
                themed(INCREASE_CONTRAST_PROPERTY, "true") { StxTheme { it() } }.increaseContrast shouldBe true
                themed(INCREASE_CONTRAST_PROPERTY, "false") { StxTheme { it() } }.increaseContrast shouldBe false
            }
        }

        feature("the desktop reads") {
            val answers = mapOf("1" to true, "0" to false)
            scenario("macOS asks defaults for each setting and reads 1 as on") {
                answers.forEach { (out, expected) ->
                    val asked = mutableListOf<List<String>>()
                    val ask = { command: List<String> ->
                        asked += command
                        out
                    }
                    val reduce = DesktopPreferences.query(true, "Mac OS X", ask)
                    val contrast = DesktopPreferences.query(false, "Mac OS X", ask)
                    withClue("output $out") { (reduce to contrast) shouldBe (expected to expected) }
                    asked.map { it.last() } shouldBe listOf("reduceMotion", "increaseContrast")
                }
            }

            scenario("Linux reads gsettings, where animations off means reduced") {
                DesktopPreferences.query(true, "Linux") { "false\n" } shouldBe true
                DesktopPreferences.query(true, "Linux") { "true\n" } shouldBe false
                DesktopPreferences.query(false, "Linux") { "true\n" } shouldBe true
                DesktopPreferences.query(false, "Linux") { null } shouldBe false
            }

            scenario("Windows reads the high-contrast flag bit, and leaves reduce motion to the property") {
                DesktopPreferences.query(false, "Windows 11") { "    Flags    REG_SZ    127\n" } shouldBe true
                DesktopPreferences.query(false, "Windows 11") { "    Flags    REG_SZ    126\n" } shouldBe false
                DesktopPreferences.query(true, "Windows 11") { error("reduce motion is not asked on Windows") } shouldBe false
            }

            scenario("a command that fails answers no preference, never an error") {
                DesktopPreferences.query(true, "Mac OS X") { null } shouldBe false
            }
        }
    })
