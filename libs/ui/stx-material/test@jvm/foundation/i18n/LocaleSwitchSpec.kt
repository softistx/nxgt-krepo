package com.softistx.material.foundation.i18n

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.softistx.material.foundation.theme.LocalStxStrings
import com.softistx.material.foundation.theme.StxTheme
import io.kotest.core.spec.style.FeatureSpec
import java.util.Locale

@OptIn(ExperimentalTestApi::class)
class LocaleSwitchSpec :
    FeatureSpec({
        feature("the library's text follows the strings the theme provides") {
            scenario("switching the strings state re-reads the text") {
                runComposeUiTest {
                    var strings by mutableStateOf(StxStrings.En)
                    setContent { StxTheme(strings = strings) { Text(LocalStxStrings.current.retry) } }
                    onNodeWithText("Retry").assertExists()
                    strings = StxStrings.Fr
                    waitForIdle()
                    onNodeWithText("Réessayer").assertExists()
                }
            }
            scenario("with no strings given the theme resolves the device locale") {
                val saved = Locale.getDefault()
                try {
                    Locale.setDefault(Locale.FRENCH)
                    runComposeUiTest {
                        setContent { StxTheme { Text(LocalStxStrings.current.retry) } }
                        onNodeWithText("Réessayer").assertExists()
                    }
                } finally {
                    Locale.setDefault(saved)
                }
            }
        }
    })
