package com.softistx.i18n

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.Locale

/**
 * Catching translation drift, which nothing else does.
 *
 * The fixtures here are deliberately drifted the way a real project drifts — French is missing two
 * keys English has, and carries one nobody else does — because that is what the audit exists to
 * report, and a spec against tidy catalogs would prove nothing.
 */
class CatalogAuditTest :
    FeatureSpec({

        feature("catalogs that have drifted") {
            scenario("a locale is told what it cannot answer, and what only it has") {
                val audit = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH)).audit()

                audit.isClean shouldBe false
                val french = audit.locales.single { it.locale == Locale.FRENCH }
                french.missing shouldBe setOf("only.in.english", "positional.greeting")
                french.extra shouldBe setOf("accented.message")
            }

            scenario("the report says enough to act on without opening the files") {
                val audit = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH)).audit()

                "$audit" shouldContain "fr:"
                "$audit" shouldContain "missing only.in.english, positional.greeting"
                "$audit" shouldContain "only here accented.message"
            }
        }

        feature("a regional overlay") {
            scenario("it is measured through its own chain, not against the whole catalog") {
                /* fr-CA overrides one key. Measured on its own it would look 5 keys short, and an
                   audit that cries wolf about every overlay is one nobody runs. */
                val audit =
                    Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH, Locale.CANADA_FRENCH)).audit()

                val quebec = audit.locales.single { it.locale == Locale.CANADA_FRENCH }
                quebec.missing shouldBe setOf("only.in.english", "positional.greeting") // fr's gaps, not its own
                quebec.extra shouldBe emptySet()
            }
        }

        feature("catalogs that agree") {
            scenario("a clean audit says so, and is the one-line spec a project should own") {
                val audit =
                    Messages
                        .of(
                            Locale.ENGLISH to mapOf("a" to "A", "b" to "B"),
                            Locale.FRENCH to mapOf("a" to "A", "b" to "B"),
                        ).audit()

                audit.isClean shouldBe true
                "$audit" shouldContain "every catalog matches en"
            }
        }
    })
