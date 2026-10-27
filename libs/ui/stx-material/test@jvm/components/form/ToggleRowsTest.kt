package com.softistx.material.components.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.height
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

/**
 * A toggle row is one target: it has the control's role, it is at least 48 dp tall, and clicking
 * its label (not only the box) toggles it.
 */
@OptIn(ExperimentalTestApi::class)
class ToggleRowsTest :
    FeatureSpec({
        feature("Checkbox") {
            scenario("the row is a 48 dp checkbox and a click on its label toggles it") {
                runComposeUiTest {
                    var checked by mutableStateOf(false)
                    setContent {
                        StxTheme(isDark = false) {
                            Checkbox(value = checked, onValueChange = { checked = it }, label = "Terms")
                        }
                    }
                    val row = onNodeWithText("Terms").assert(role(Role.Checkbox))
                    row.getUnclippedBoundsInRoot().height.value shouldBe 48f
                    row.performClick()
                    checked shouldBe true
                }
            }
            scenario("the caller's modifier lands on the outermost node") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            Checkbox(
                                value = false,
                                onValueChange = {},
                                label = "Terms",
                                helper = "Read them",
                                modifier = Modifier.testTag("outer"),
                            )
                        }
                    }
                    // The tag sits on the whole component: the row plus the helper line under it.
                    (onNodeWithTag("outer").getUnclippedBoundsInRoot().height.value > 48f) shouldBe true
                }
            }
        }

        feature("Switch") {
            scenario("the row is a 48 dp switch and a click toggles it") {
                runComposeUiTest {
                    var on by mutableStateOf(false)
                    setContent {
                        StxTheme(isDark = false) {
                            Switch(value = on, onValueChange = { on = it }, label = "Digest")
                        }
                    }
                    val row = onNodeWithText("Digest").assert(role(Role.Switch))
                    (row.getUnclippedBoundsInRoot().height.value >= 48f) shouldBe true
                    row.performClick()
                    on shouldBe true
                }
            }
        }

        feature("RadioGroup") {
            scenario("each option is a 48 dp radio button and a click selects it") {
                runComposeUiTest {
                    var picked by mutableStateOf("Monthly")
                    setContent {
                        StxTheme(isDark = false) {
                            RadioGroup(
                                value = picked,
                                onValueChange = { picked = it },
                                options = listOf("Monthly", "Yearly"),
                            )
                        }
                    }
                    val row = onNodeWithText("Yearly").assert(role(Role.RadioButton))
                    row.getUnclippedBoundsInRoot().height.value shouldBe 48f
                    row.performClick()
                    picked shouldBe "Yearly"
                }
            }
        }
    })
