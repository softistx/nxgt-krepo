package com.softistx.material.components.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.softistx.material.components.content.ListTile
import com.softistx.material.components.surface.Accordion
import com.softistx.material.components.surface.AccordionItem
import com.softistx.material.components.surface.Disclosure
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
private val focusable = SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)

private fun SemanticsNodeInteraction.shouldBeFocusableButton(): SemanticsNodeInteraction =
    assert(button).assert(focusable).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))

/** Each clickable row announces itself as a button and can take keyboard focus. */
@OptIn(ExperimentalTestApi::class)
class ClickableRolesTest :
    FeatureSpec({
        feature("a clickable row") {
            scenario("ListTile is a focusable button") {
                runComposeUiTest {
                    var clicks by mutableStateOf(0)
                    setContent { StxTheme(strings = StxStrings.En, isDark = false) { ListTile(title = "Profile", onClick = { clicks++ }) } }
                    onNodeWithText("Profile").shouldBeFocusableButton().performClick()
                    clicks shouldBe 1
                }
            }
            scenario("a Breadcrumb ancestor is a focusable button") {
                runComposeUiTest {
                    var clicks by mutableStateOf(0)
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            Breadcrumb(
                                items = listOf(BreadcrumbItem("Home", { clicks++ }), BreadcrumbItem("Here")),
                            )
                        }
                    }
                    onNodeWithText("Home").shouldBeFocusableButton().performClick()
                    clicks shouldBe 1
                }
            }
            scenario("a completed Step is a focusable button") {
                runComposeUiTest {
                    var went by mutableStateOf(-1)
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            Stepper(
                                steps = listOf(Step("Account"), Step("Plan"), Step("Pay")),
                                current = 1,
                                onStep = { went = it },
                            )
                        }
                    }
                    onNodeWithText("Account").shouldBeFocusableButton().performClick()
                    went shouldBe 0
                }
            }
            scenario("an Accordion header is a focusable button") {
                runComposeUiTest {
                    var open by mutableStateOf<Int?>(null)
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            Accordion(
                                items = listOf(AccordionItem("One", "first"), AccordionItem("Two", "second")),
                                expanded = open,
                                onExpandedChange = { open = it },
                            )
                        }
                    }
                    onNodeWithText("Two").shouldBeFocusableButton().performClick()
                    open shouldBe 1
                }
            }
            scenario("a Disclosure header is a focusable button") {
                runComposeUiTest {
                    var open by mutableStateOf(false)
                    setContent {
                        StxTheme(strings = StxStrings.En, isDark = false) {
                            Disclosure(title = "More", expanded = open, onExpandedChange = { open = it }) {
                                Typography(text = "body")
                            }
                        }
                    }
                    onNodeWithText("More").shouldBeFocusableButton().performClick()
                    open shouldBe true
                }
            }
        }
    })
