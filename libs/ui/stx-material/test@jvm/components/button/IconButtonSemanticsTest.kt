package com.softistx.material.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

private fun toggled(state: ToggleableState) = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

/** The morphing controls keep the roles, states and 48 dp targets M3 gave them. */
@OptIn(ExperimentalTestApi::class)
class IconButtonSemanticsTest :
    FeatureSpec({
        feature("the semantics of the button family") {
            scenario("IconButton is a Role.Button with a 48 dp target") {
                runComposeUiTest {
                    var clicks = 0
                    setContent { StxTheme(isDark = false) { IconButton(StxIcons.Add, "Add", onClick = { clicks++ }) } }
                    onNodeWithContentDescription("Add")
                        .assert(role(Role.Button))
                        .assertTouchHeightIsEqualTo(48.dp)
                        .assertTouchWidthIsEqualTo(48.dp)
                        .performClick()
                    clicks shouldBe 1
                }
            }

            scenario("IconToggle is a Role.Checkbox whose ToggleableState follows checked") {
                runComposeUiTest {
                    var checked by mutableStateOf(false)
                    setContent {
                        StxTheme(isDark = false) {
                            IconToggle(StxIcons.Add, "Pin", checked = checked, onCheckedChange = { checked = it })
                        }
                    }
                    onNodeWithContentDescription("Pin")
                        .assert(role(Role.Checkbox))
                        .assert(toggled(ToggleableState.Off))
                        .assertTouchHeightIsEqualTo(48.dp)
                        .performClick()
                    checked shouldBe true
                    onNodeWithContentDescription("Pin").assert(toggled(ToggleableState.On))
                }
            }

            scenario("ToggleButton is a Role.Checkbox with its state") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) { ToggleButton("Follow", checked = true, onCheckedChange = {}) }
                    }
                    onNodeWithText("Follow").assert(role(Role.Checkbox)).assert(toggled(ToggleableState.On))
                }
            }

            scenario("both halves of a SplitButton are Role.Button") {
                runComposeUiTest {
                    setContent {
                        StxTheme(isDark = false) {
                            SplitButton("Save", onClick = {}, overflow = listOf(MenuItem("Save as", onClick = {})))
                        }
                    }
                    onNodeWithText("Save").assert(role(Role.Button))
                    onNodeWithContentDescription("More actions").assert(role(Role.Button))
                }
            }

            scenario("Fab is a Role.Button with a 48 dp target") {
                runComposeUiTest {
                    setContent { StxTheme(isDark = false) { Fab(StxIcons.Add, "New", onClick = {}) } }
                    onNodeWithContentDescription("New").assert(role(Role.Button)).assertHeightIsAtLeast(48.dp)
                }
            }
        }

        feature("the button-family style") {
            scenario("morphingButton is the same style under its old name") {
                @Suppress("DEPRECATION")
                (
                    com.softistx.material.foundation.style.StxStyles.morphingButton shouldBe
                        com.softistx.material.foundation.style.StxStyles.button
                )
            }
        }
    })
