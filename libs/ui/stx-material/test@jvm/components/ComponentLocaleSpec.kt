package com.softistx.material.components

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.entryProvider
import com.softistx.material.components.content.FileChip
import com.softistx.material.components.data.Pagination
import com.softistx.material.components.datetime.RelativeTime
import com.softistx.material.components.form.PasswordMeter
import com.softistx.material.components.form.QuantityField
import com.softistx.material.components.form.TextField
import com.softistx.material.components.form.ThemeToggle
import com.softistx.material.components.layout.LoadMoreButton
import com.softistx.material.components.layout.SelectionBar
import com.softistx.material.components.media.SeenBy
import com.softistx.material.components.navigation.AdaptiveNavDisplay
import com.softistx.material.components.navigation.ListDetail
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.theme.ColorMode
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ComponentLocaleSpec :
    FeatureSpec({
        feature("components read their labels from the theme's strings") {
            scenario("Pagination names its buttons in French") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) {
                            Pagination(hasPrevious = true, hasNext = true, onPrevious = {}, onNext = {})
                        }
                    }
                    onNodeWithText("Précédent").assertExists()
                    onNodeWithText("Suivant").assertExists()
                }
            }
            scenario("a chip's remove label names the file in French") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) { FileChip(name = "a.pdf", onRemove = {}) }
                    }
                    onNodeWithContentDescription(StxStrings.Fr.remove("a.pdf")).assertExists()
                }
            }
            scenario("a quantity field's buttons and a secret field's toggle follow the locale") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) {
                            QuantityField(value = 1, onValueChange = {})
                            TextField(value = "x", onValueChange = {}, secret = true)
                        }
                    }
                    onNodeWithContentDescription(StxStrings.Fr.decrease).assertExists()
                    onNodeWithContentDescription(StxStrings.Fr.increase).assertExists()
                    onNodeWithContentDescription(StxStrings.Fr.showValue).assertExists()
                }
            }
            scenario("an explicit argument still wins over the catalogue") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) {
                            LoadMoreButton(hasMore = true, loading = false, onClick = {}, label = "More please")
                        }
                    }
                    onNodeWithText("More please").assertExists()
                }
            }
            scenario("the default label is the catalogue's") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) { LoadMoreButton(hasMore = true, loading = false, onClick = {}) }
                    }
                    onNodeWithText(StxStrings.Fr.loadMore).assertExists()
                }
            }
            scenario("ThemeToggle names its modes in French") {
                runComposeUiTest {
                    setContent { StxTheme(strings = StxStrings.Fr) { ThemeToggle(value = ColorMode.System, onChange = {}) } }
                    onNodeWithText(StxStrings.Fr.themeLight).assertExists()
                    onNodeWithText(StxStrings.Fr.themeDark).assertExists()
                    onNodeWithText(StxStrings.Fr.themeSystem).assertExists()
                }
            }
            scenario("a Pagination in French still shows its own label") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) {
                            Pagination(hasPrevious = true, hasNext = true, onPrevious = {}, onNext = {}, label = "Page 2")
                        }
                    }
                    onNodeWithText("Page 2").assertExists()
                    onNodeWithText(StxStrings.Fr.next).assertExists()
                }
            }
            scenario("PasswordMeter, SelectionBar, SeenBy and RelativeTime follow the locale") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) {
                            PasswordMeter(value = "abc")
                            SelectionBar(count = 3, onClear = {})
                            SeenBy(items = emptyList())
                            RelativeTime(at = Instant.fromEpochSeconds(0), now = Instant.fromEpochSeconds(180))
                        }
                    }
                    onNodeWithText(StxStrings.Fr.passwordWeak).assertExists()
                    onNodeWithText(StxStrings.Fr.selected(3)).assertExists()
                    onNodeWithText(StxStrings.Fr.sent).assertExists()
                    onNodeWithText("il y a 3 min").assertExists()
                }
            }
            scenario("ListDetail.list reads the catalogue unless a title is given") {
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) {
                            val backStack = remember { mutableStateListOf<Any>(PaneA) }
                            AdaptiveNavDisplay(
                                backStack = backStack,
                                entryProvider = entryProvider { entry<PaneA>(metadata = ListDetail.list()) { Text("list") } },
                            )
                        }
                    }
                    onNodeWithText(StxStrings.Fr.selectAnItem).assertExists()
                }
                runComposeUiTest {
                    setContent {
                        StxTheme(strings = StxStrings.Fr) {
                            val backStack = remember { mutableStateListOf<Any>(PaneA) }
                            AdaptiveNavDisplay(
                                backStack = backStack,
                                entryProvider =
                                    entryProvider {
                                        entry<PaneA>(
                                            metadata = ListDetail.list(placeholderTitle = "Pick one"),
                                        ) { Text("list") }
                                    },
                            )
                        }
                    }
                    onNodeWithText("Pick one").assertExists()
                }
            }
        }
    })

private data object PaneA
