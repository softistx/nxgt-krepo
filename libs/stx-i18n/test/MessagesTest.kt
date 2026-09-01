package com.softistx.i18n

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

/**
 * The walk, which is the thing the implementation this was ported from got wrong.
 *
 * That one asked its chosen bundle for the key, and if the answer was no, asked the same bundle the
 * same question again — a dead branch where the fallback was meant to be. So a key present in
 * English and missing in French reached a French user as `checkout.button`. Every scenario here is
 * about the order in which catalogs are consulted.
 */
class MessagesTest :
    FeatureSpec({

        val messages =
            Messages.load(
                locales = listOf(Locale.ENGLISH, Locale.FRENCH, Locale.CANADA_FRENCH),
                fallback = Locale.ENGLISH,
            )

        feature("finding a message") {
            scenario("a key the locale has comes from the locale") {
                messages.forLocale(Locale.FRENCH)["orders.title"] shouldBe "Commandes"
            }

            scenario("a key the locale is missing falls back per key, not per catalog") {
                /* The bug this module exists to not have: French has no translation for this one,
                   so a French reader gets the English sentence rather than the key. */
                messages.forLocale(Locale.FRENCH)["only.in.english"] shouldBe "Nobody has translated this yet"
            }

            scenario("a region walks up to its language before it leaves for the fallback") {
                val quebec = messages.forLocale(Locale.CANADA_FRENCH)

                quebec["checkout.button"] shouldBe "Passer la commande maintenant" // fr-CA's own
                quebec["orders.title"] shouldBe "Commandes" // fr's
                quebec["only.in.english"] shouldBe "Nobody has translated this yet" // en's
            }

            scenario("a locale with no catalog at all still answers, from the fallback") {
                messages.forLocale(Locale.of("es"))["orders.title"] shouldBe "Orders"
            }
        }

        feature("the JVM's own default locale") {
            scenario("it never leaks into the answer") {
                /* ResourceBundle.getBundle falls back to the default locale, so on a French machine
                   a request for Spanish quietly returns French — a bug that cannot be reproduced on
                   the laptop that reported it. Nothing here calls getBundle. */
                val original = Locale.getDefault()
                try {
                    Locale.setDefault(Locale.FRENCH)

                    val spanish = Messages.load(locales = listOf(Locale.ENGLISH)).forLocale(Locale.of("es"))

                    spanish["orders.title"] shouldBe "Orders"
                } finally {
                    Locale.setDefault(original)
                }
            }
        }

        feature("loading") {
            scenario("a pattern ICU cannot parse fails here, not on the screen that uses it") {
                /* Eager compilation is the whole reason: an unclosed plural is a failed startup
                   rather than a failure in the one language nobody on the team reads. */
                val failure =
                    shouldThrow<MalformedMessageException> {
                        Messages.load(source = PropertiesSource(baseName = "broken/malformed"), locales = emptyList())
                    }

                failure.key shouldBe "broken.pattern"
            }

            scenario("the locales it can serve include the fallback, catalogue of its own or not") {
                /* English here lives in the unsuffixed messages.properties rather than a
                   messages_en.properties, and is served all the same — which matters because this
                   set is what a negotiation is matched against. */
                messages.locales shouldBe setOf(Locale.ENGLISH, Locale.FRENCH, Locale.CANADA_FRENCH)
            }
        }
    })
