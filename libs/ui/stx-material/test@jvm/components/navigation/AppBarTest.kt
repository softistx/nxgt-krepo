package com.softistx.material.components.navigation

import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.components.button.IconButton
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.text.style
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

private const val TITLE = "Orders"
private const val LONG_TITLE = "Quarterly attendance and behaviour report for the whole of the upper school"

/** The styles the title nodes of an [AppBar] with this [size] were laid out with. */
@OptIn(ExperimentalTestApi::class)
private fun titleStyles(size: AppBarSize): List<TextStyle> {
    var styles = emptyList<TextStyle>()
    runComposeUiTest {
        setContent { StxTheme(isDark = false) { AppBar(TITLE, size = size) } }
        styles =
            onAllNodes(hasText(TITLE))
                .fetchSemanticsNodes()
                .mapNotNull { node ->
                    val results = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                    results.firstOrNull()?.layoutInput?.style
                }
    }
    return styles
}

/** The expanded height of the flexible bar: Medium 112 dp (unchanged), Large 120 dp. */
private fun expanded(size: AppBarSize): Dp =
    when (size) {
        AppBarSize.Medium -> TopAppBarDefaults.MediumFlexibleAppBarWithoutSubtitleExpandedHeight
        else -> TopAppBarDefaults.LargeFlexibleAppBarWithoutSubtitleExpandedHeight
    }

/** The expanded height of the plain M3 bar this change replaced, measured off M3's own composable: Medium 112 dp, Large 152 dp. */
@OptIn(ExperimentalTestApi::class)
private fun plainBarHeight(size: AppBarSize): Dp {
    var height = 0.dp
    runComposeUiTest {
        setContent {
            StxTheme(isDark = false) {
                val modifier = Modifier.testTag("old")
                if (size == AppBarSize.Medium) {
                    MediumTopAppBar(title = { Text(TITLE) }, modifier = modifier)
                } else {
                    LargeTopAppBar(title = { Text(TITLE) }, modifier = modifier)
                }
            }
        }
        height = onNodeWithTag("old").getUnclippedBoundsInRoot().let { it.bottom - it.top }
    }
    return height
}

/** The top bar: flexible Medium and Large bars, the emphasised heading title, and every size's scroll behaviour. */
@OptIn(ExperimentalTestApi::class)
class AppBarTest :
    FeatureSpec({
        feature("the title") {
            scenario("every size's own title text node is a heading, not just a node around it") {
                AppBarSize.entries.forEach { size ->
                    runComposeUiTest {
                        setContent { StxTheme(isDark = false) { AppBar(TITLE, size = size) } }
                        val texts = onAllNodes(hasText(TITLE), useUnmergedTree = true).fetchSemanticsNodes()
                        texts.size shouldBeGreaterThan 0
                        texts.forEach { it.config.contains(SemanticsProperties.Heading) shouldBe true }
                    }
                }
            }

            scenario("a Small and a Centered bar set it in the emphasised title-large, heavier than the plain one") {
                var emphasised = TextStyle.Default
                var plain = TextStyle.Default
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            emphasised = TypographyVariant.TitleLarge.style(true)
                            plain = TypographyVariant.TitleLarge.style(false)
                        }
                    }
                }
                listOf(AppBarSize.Small, AppBarSize.Centered).forEach { size ->
                    val styles = titleStyles(size)
                    styles.size shouldBeGreaterThan 0
                    styles.forEach {
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

            scenario("a long title with a navigation icon and two actions keeps a Small and a Centered bar at 64 dp") {
                listOf(AppBarSize.Small, AppBarSize.Centered).forEach { size ->
                    runComposeUiTest {
                        setContent {
                            StxTheme(isDark = false) {
                                AppBar(
                                    title = LONG_TITLE,
                                    modifier = Modifier.testTag("bar"),
                                    size = size,
                                    navigationIcon = StxIcons.Menu,
                                    onNavigation = {},
                                ) {
                                    IconButton(icon = StxIcons.Search, description = "Search", onClick = {})
                                    IconButton(icon = StxIcons.Star, description = "Star", onClick = {})
                                }
                            }
                        }
                        onNodeWithTag("bar").assertHeightIsEqualTo(64.dp)
                        val lines =
                            onAllNodes(hasText(LONG_TITLE)).fetchSemanticsNodes().mapNotNull { node ->
                                val results = mutableListOf<TextLayoutResult>()
                                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                                results.firstOrNull()?.lineCount
                            }
                        lines.size shouldBeGreaterThan 0
                        lines.forEach { it shouldBe 1 }
                    }
                }
            }
        }

        feature("the scroll behaviour") {
            listOf(AppBarSize.Medium, AppBarSize.Large).forEach { size ->
                scenario("a $size bar is M3's flexible height (Medium 112 dp, was 112; Large 120 dp, was 152), then collapses to 64 dp") {
                    runComposeUiTest {
                        lateinit var behavior: TopAppBarScrollBehavior
                        setContent {
                            StxTheme(isDark = false) {
                                behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                                AppBar(TITLE, Modifier.testTag("bar"), size, scrollBehavior = behavior)
                            }
                        }
                        plainBarHeight(size) shouldBe (if (size == AppBarSize.Medium) 112.dp else 152.dp)
                        onNodeWithTag("bar").assertHeightIsEqualTo(if (size == AppBarSize.Medium) 112.dp else 120.dp)
                        expanded(size) shouldBe (if (size == AppBarSize.Medium) 112.dp else 120.dp)
                        runOnIdle { behavior.state.heightOffset = behavior.state.heightOffsetLimit }
                        waitForIdle()
                        onNodeWithTag("bar").assertHeightIsEqualTo(64.dp)
                    }
                }
            }

            listOf(AppBarSize.Small, AppBarSize.Centered).forEach { size ->
                scenario("a $size bar given enterAlways slides away: it is shorter once scrolled") {
                    runComposeUiTest {
                        lateinit var behavior: TopAppBarScrollBehavior
                        setContent {
                            StxTheme(isDark = false) {
                                behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
                                AppBar(TITLE, Modifier.testTag("bar"), size, scrollBehavior = behavior)
                            }
                        }
                        onNodeWithTag("bar").assertHeightIsEqualTo(64.dp)
                        runOnIdle { behavior.state.heightOffset = behavior.state.heightOffsetLimit }
                        waitForIdle()
                        onNodeWithTag("bar").getUnclippedBoundsInRoot().let { it.bottom.value - it.top.value } shouldBeLessThan 64f
                    }
                }
            }
        }

        feature("the signature") {
            scenario("signature guard: a trailing actions lambda still compiles after the scroll behaviour") {
                runComposeUiTest {
                    setContent { StxTheme { AppBar("Orders") { } } }
                }
            }
        }
    })
