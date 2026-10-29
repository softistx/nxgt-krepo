package com.softistx.material.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.softistx.material.components.content.FileChip
import com.softistx.material.components.data.Pagination
import com.softistx.material.components.form.QuantityField
import com.softistx.material.components.form.TextField
import com.softistx.material.components.layout.LoadMoreButton
import com.softistx.material.foundation.i18n.StxStrings
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec

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
        }
    })
