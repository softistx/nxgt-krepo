package com.softistx.material.foundation.i18n

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

/** The words that are the same in English and in French. */
private val SameInBoth = setOf("ok", "date", "dates")

private val Both = listOf(StxStrings.En, StxStrings.Fr)

class StxStringsTest :
    FeatureSpec({
        feature("forLanguage") {
            scenario("a French tag gives the French catalogue, whatever its region or case") {
                StxStrings.forLanguage("fr") shouldBe StxStrings.Fr
                StxStrings.forLanguage("fr-CA") shouldBe StxStrings.Fr
                StxStrings.forLanguage("fr_BE") shouldBe StxStrings.Fr
                StxStrings.forLanguage("FR") shouldBe StxStrings.Fr
            }
            scenario("an unknown or empty tag falls back to English") {
                StxStrings.forLanguage("de") shouldBe StxStrings.En
                StxStrings.forLanguage("") shouldBe StxStrings.En
                StxStrings.forLanguage("en-GB") shouldBe StxStrings.En
            }
        }

        feature("the catalogues") {
            scenario("no entry is blank, in either locale") {
                for (s in Both) for ((key, value) in s.entries()) withClue(key) { value.shouldNotBeBlank() }
            }
            scenario("French differs from English outside the allow-list") {
                val en = StxStrings.En.entries()
                for ((key, value) in StxStrings.Fr.entries()) {
                    if (key !in SameInBoth) withClue(key) { value shouldNotBe en.getValue(key) }
                }
            }
            scenario("both catalogues list the same keys, and the allow-list names real ones") {
                StxStrings.Fr.entries().keys shouldBe StxStrings.En.entries().keys
                StxStrings.En
                    .entries()
                    .keys
                    .containsAll(SameInBoth) shouldBe true
            }
            scenario("French plurals keep zero and one singular") {
                StxStrings.Fr.selected(0) shouldBe "0 sélectionné"
                StxStrings.Fr.selected(1) shouldBe "1 sélectionné"
                StxStrings.Fr.selected(2) shouldBe "2 sélectionnés"
            }
            scenario("copy overrides one string and keeps the rest") {
                val custom = StxStrings.En.copy(retry = "Try again")
                custom.retry shouldBe "Try again"
                custom.cancel shouldBe StxStrings.En.cancel
            }
        }
    })
