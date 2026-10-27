package com.softistx.material.components.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.text.style
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/** The styles the title nodes of an [AppBar] with this [size] were laid out with. */
@OptIn(ExperimentalTestApi::class)
private fun titleStyles(size: AppBarSize): List<TextStyle> {
    var styles = emptyList<TextStyle>()
    runComposeUiTest {
        setContent { StxTheme(isDark = false) { AppBar("Orders", size = size) } }
        styles =
            onAllNodes(hasText("Orders"))
                .fetchSemanticsNodes()
                .mapNotNull { node ->
                    val results = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                    results.firstOrNull()?.layoutInput?.style
                }
    }
    return styles
}

/** The top bar: the flexible Medium and Large bars, the emphasised heading title, and the collapse. */
@OptIn(ExperimentalTestApi::class)
class AppBarTest :
    FeatureSpec({
        feature("the title") {
            scenario("every size announces it as a heading") {
                AppBarSize.entries.forEach { size ->
                    runComposeUiTest {
                        setContent { StxTheme(isDark = false) { AppBar("Orders", size = size) } }
                        val headings =
                            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasText("Orders"))
                        headings.fetchSemanticsNodes().size shouldBeGreaterThan 0
                    }
                }
            }

            scenario("a Small and a Centered bar set it in the emphasised headline, heavier than the plain one") {
                var emphasised = TextStyle.Default
                var plain = TextStyle.Default
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            emphasised = TypographyVariant.HeadlineSmall.style(true)
                            plain = TypographyVariant.HeadlineSmall.style(false)
                        }
                    }
                }
                listOf(AppBarSize.Small, AppBarSize.Centered).forEach { size ->
                    titleStyles(size).forEach {
                        it.fontSize shouldBe emphasised.fontSize
                        it.fontWeight shouldBe emphasised.fontWeight
                    }
                }
                (emphasised.fontWeight as FontWeight).weight shouldBeGreaterThan (plain.fontWeight as FontWeight).weight
            }

            scenario("a Medium bar sets it in headline-medium and a Large bar in display-small, M3's flexible type") {
                var headline = TextStyle.Default
                var display = TextStyle.Default
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            headline = MaterialTheme.typography.headlineMedium
                            display = MaterialTheme.typography.displaySmall
                        }
                    }
                }
                titleStyles(AppBarSize.Medium).maxOf { it.fontSize.value } shouldBe headline.fontSize.value
                titleStyles(AppBarSize.Large).maxOf { it.fontSize.value } shouldBe display.fontSize.value
            }
        }

        feature("the collapse") {
            scenario("a Large bar given an exit-until-collapsed behaviour shrinks to the small bar's 64 dp") {
                runComposeUiTest {
                    lateinit var state: TopAppBarState
                    setContent {
                        StxTheme(isDark = false) {
                            val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                            state = behavior.state
                            AppBar(
                                title = "Orders",
                                modifier = Modifier.testTag("bar"),
                                size = AppBarSize.Large,
                                scrollBehavior = behavior,
                            )
                        }
                    }
                    onNodeWithTag("bar").assertHeightIsEqualTo(120.dp)
                    runOnIdle { state.heightOffset = state.heightOffsetLimit }
                    waitForIdle()
                    onNodeWithTag("bar").assertHeightIsEqualTo(64.dp)
                }
            }
        }

        feature("the signature") {
            scenario("a trailing actions lambda still compiles after the scroll behaviour") {
                runComposeUiTest {
                    setContent { StxTheme { AppBar("Orders") { } } }
                }
            }
        }
    })
