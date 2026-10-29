package com.softistx.material.components.navigation

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.textLayout
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

private const val LONG_LABEL = "Parent-teacher conferences"

private val labels = listOf("Dashboard", "Timetable", "Messages", "Attendance", "Homework")

/** On a phone the bar's labels each stay on one line, whatever the destination is called. */
@OptIn(ExperimentalTestApi::class)
class NavigationSuiteLabelTest :
    FeatureSpec({
        feature("the compact bar") {
            scenario("keeps every label on one line at phone width") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
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
                    labels.forEach { onNodeWithText(it, useUnmergedTree = true).textLayout()!!.lineCount shouldBe 1 }
                }
            }
            scenario("ellipsizes a label that cannot fit on one line") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            CompositionLocalProvider(LocalWindowInfo provides phone) {
                                NavigationSuite(
                                    destinations =
                                        listOf(
                                            NavigationDestination(LONG_LABEL, StxIcons.Home),
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
                    val label = onNodeWithText(LONG_LABEL, useUnmergedTree = true)
                    label.assertExists()
                    label.textLayout()!!.lineCount shouldBe 1
                    label.textLayout()!!.hasVisualOverflow shouldBe true
                }
            }
        }
    })
