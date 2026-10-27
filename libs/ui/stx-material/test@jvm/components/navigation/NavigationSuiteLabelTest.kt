package com.softistx.material.components.navigation

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private const val PHONE_WIDTH = 360
private const val PHONE_HEIGHT = 640

/** A 360 x 640 window at density 1, the width of the phones the School bench shoots. */
private val phone =
    object : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize = IntSize(PHONE_WIDTH, PHONE_HEIGHT)
        override val containerDpSize: DpSize = DpSize(PHONE_WIDTH.dp, PHONE_HEIGHT.dp)
    }

private val labels = listOf("Dashboard", "Timetable", "Messages", "Attendance", "Homework")

private fun SemanticsNodeInteraction.lineCount(): Int {
    val results = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.first().lineCount
}

/** On a phone the bar's labels each stay on one line, whatever the destination is called. */
@OptIn(ExperimentalTestApi::class)
class NavigationSuiteLabelTest :
    FeatureSpec({
        feature("the compact bar") {
            scenario("keeps every label on one line at phone width") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            CompositionLocalProvider(LocalWindowInfo provides phone) {
                                NavigationSuite(
                                    destinations = labels.map { NavigationDestination(it, StxIcons.Home) },
                                    selected = 0,
                                    onSelect = {},
                                    modifier = Modifier.requiredSize(PHONE_WIDTH.dp, PHONE_HEIGHT.dp),
                                ) {}
                            }
                        }
                    }
                    labels.forEach { onNodeWithText(it, useUnmergedTree = true).lineCount() shouldBe 1 }
                }
            }
            scenario("ellipsizes a label that cannot fit on one line") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            CompositionLocalProvider(LocalWindowInfo provides phone) {
                                NavigationSuite(
                                    destinations =
                                        listOf(
                                            NavigationDestination("Parent-teacher conferences", StxIcons.Home),
                                            NavigationDestination("Messages", StxIcons.Send),
                                            NavigationDestination("Timetable", StxIcons.Calendar),
                                            NavigationDestination("Attendance", StxIcons.Check),
                                        ),
                                    selected = 0,
                                    onSelect = {},
                                    modifier = Modifier.requiredSize(PHONE_WIDTH.dp, PHONE_HEIGHT.dp),
                                ) {}
                            }
                        }
                    }
                    onNodeWithText("Parent-teacher conferences", useUnmergedTree = true).lineCount() shouldBe 1
                }
            }
        }
    })
