package com.softistx.material.foundation.i18n

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlin.reflect.KProperty1

/** The words that are the same in English and in French. */
private val SameInBoth = setOf("ok", "date", "dates")

private val Both = listOf(StxStrings.En, StxStrings.Fr)

private val Fixed: List<KProperty1<StxStrings, String>> =
    listOf(
        StxStrings::ok,
        StxStrings::cancel,
        StxStrings::confirm,
        StxStrings::confirmQuestion,
        StxStrings::close,
        StxStrings::save,
        StxStrings::edit,
        StxStrings::retry,
        StxStrings::loading,
        StxStrings::loadFailed,
        StxStrings::previous,
        StxStrings::next,
        StxStrings::continueStep,
        StxStrings::back,
        StxStrings::search,
        StxStrings::more,
        StxStrings::moreActions,
        StxStrings::moreInformation,
        StxStrings::dismiss,
        StxStrings::dismissReply,
        StxStrings::unpin,
        StxStrings::send,
        StxStrings::attachFile,
        StxStrings::writeMessage,
        StxStrings::decrease,
        StxStrings::increase,
        StxStrings::showValue,
        StxStrings::hideValue,
        StxStrings::chooseOne,
        StxStrings::addTitle,
        StxStrings::addTag,
        StxStrings::uploadPrompt,
        StxStrings::uploadHint,
        StxStrings::optional,
        StxStrings::dangerZone,
        StxStrings::passwordWeak,
        StxStrings::passwordFair,
        StxStrings::passwordGood,
        StxStrings::passwordStrong,
        StxStrings::date,
        StxStrings::pickDate,
        StxStrings::dates,
        StxStrings::pickDates,
        StxStrings::time,
        StxStrings::pickTime,
        StxStrings::loadMore,
        StxStrings::backToTop,
        StxStrings::selectAnItem,
        StxStrings::clearSelection,
        StxStrings::justNow,
        StxStrings::yesterday,
        StxStrings::openActions,
        StxStrings::closeActions,
        StxStrings::copy,
        StxStrings::copied,
        StxStrings::sortBy,
        StxStrings::ascending,
        StxStrings::descending,
        StxStrings::command,
        StxStrings::typeACommand,
        StxStrings::noMatchingCommands,
        StxStrings::sent,
        StxStrings::imageLoadFailed,
        StxStrings::someoneIsTyping,
        StxStrings::themeLight,
        StxStrings::themeDark,
        StxStrings::themeSystem,
        StxStrings::readMore,
        StxStrings::showLess,
    )

private val Counted: Map<String, (StxStrings, Int) -> String> =
    mapOf(
        "selected" to { s, n -> s.selected(n) },
        "inMinutes" to { s, n -> s.inMinutes(n) },
        "inHours" to { s, n -> s.inHours(n) },
        "minutesAgo" to { s, n -> s.minutesAgo(n) },
        "hoursAgo" to { s, n -> s.hoursAgo(n) },
        "daysAgo" to { s, n -> s.daysAgo(n) },
        "seenBy" to { s, n -> s.seenBy(n) },
    )

private val Named: Map<String, (StxStrings) -> String> =
    mapOf(
        "copyCode" to { it.copyCode("SAVE10") },
        "sortByColumn" to { it.sortByColumn("Name") },
        "remove" to { it.remove("Ada") },
        "ratingOf" to { it.ratingOf(3, 5) },
    )

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
            scenario("no fixed string is blank, in either locale") {
                for (s in Both) for (p in Fixed) p.get(s).shouldNotBeBlank()
            }
            scenario("French differs from English outside the allow-list") {
                for (p in Fixed) {
                    if (p.name !in SameInBoth) p.get(StxStrings.Fr) shouldNotBe p.get(StxStrings.En)
                }
            }
            scenario("every allow-listed word really is a key") {
                SameInBoth shouldBe Fixed.map { it.name }.filter { it in SameInBoth }.toSet()
            }
            scenario("a counted string is not blank for 0, 1 and 2") {
                for (s in Both) {
                    for (f in Counted.values) for (n in 0..2) f(s, n).shouldNotBeBlank()
                    for (f in Named.values) f(s).shouldNotBeBlank()
                }
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
