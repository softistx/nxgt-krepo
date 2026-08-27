package com.strange.i18n

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.Locale

/**
 * Formatting, and what happens when it cannot be done.
 *
 * The plural scenarios are the reason ICU is a dependency at all: `{count, plural, …}` is a rule
 * the *translator* writes, in their own language's terms, and no amount of Kotlin at the call site
 * substitutes for it — Polish has four plural forms and Japanese has none.
 */
class TranslatorTest :
    FeatureSpec({

        val messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH))

        feature("a message with no arguments") {
            scenario("it is returned as it is, whatever arguments were offered") {
                messages.forLocale(Locale.ENGLISH)["orders.title"] shouldBe "Orders"
                messages.forLocale(Locale.ENGLISH).translate("orders.title", "ignored") shouldBe "Orders"
            }
        }

        feature("named arguments") {
            scenario("they are substituted, in the locale that supplied the message") {
                messages.forLocale(Locale.ENGLISH).translate("hello.world", mapOf("name" to "Ada")) shouldBe "Hello Ada!"
                messages.forLocale(Locale.FRENCH).translate("hello.world", mapOf("name" to "Ada")) shouldBe "Bonjour Ada !"
            }
        }

        feature("positional arguments") {
            scenario("they still work, for a pattern written that way") {
                messages
                    .forLocale(Locale.ENGLISH)
                    .translate("positional.greeting", "Ada", 42) shouldBe "Hello Ada, you are visitor 42"
            }
        }

        feature("plurals") {
            scenario("the rule belongs to the message, so each language brings its own") {
                val english = messages.forLocale(Locale.ENGLISH)

                english.translate("orders.count", mapOf("count" to 1)) shouldBe "1 order"
                english.translate("orders.count", mapOf("count" to 5)) shouldBe "5 orders"
            }

            scenario("and French draws the line in a different place than English does") {
                /* French treats zero as singular where English does not — a rule in the catalog,
                   not in the caller. */
                val french = messages.forLocale(Locale.FRENCH)

                french.translate("orders.count", mapOf("count" to 1)) shouldBe "1 commande"
                french.translate("orders.count", mapOf("count" to 5)) shouldBe "5 commandes"
            }
        }

        feature("a key nothing answers") {
            scenario("production gets the key back, because a visible key beats a failed request") {
                messages.forLocale(Locale.FRENCH)["nobody.has.this"] shouldBe "nobody.has.this"
            }

            scenario("a test gets an exception, naming where it looked") {
                val strict =
                    Messages.load(
                        locales = listOf(Locale.FRENCH),
                        missingKey = MissingKey.Fail,
                    )

                val failure = shouldThrow<MissingMessageException> { strict.forLocale(Locale.FRENCH)["nobody.has.this"] }

                failure.key shouldBe "nobody.has.this"
                failure.locales shouldBe listOf(Locale.FRENCH, Locale.ENGLISH)
            }
        }

        feature("arguments that do not fit their pattern") {
            scenario("production is left with the placeholder standing, which is ICU's own answer") {
                /* Wrong, visible and survivable — the same trade as a missing key, and as
                   diagnosable: `{count}` on a page names the argument that was not passed. */
                messages.forLocale(Locale.ENGLISH).translate("orders.count", mapOf("wrong" to 1)) shouldBe "{count}"
            }

            scenario("a test is told which key and which locale") {
                val strict = Messages.load(locales = listOf(Locale.ENGLISH), missingKey = MissingKey.Fail)

                val failure =
                    shouldThrow<MalformedMessageException> {
                        strict.forLocale(Locale.ENGLISH).translate("orders.count", mapOf("wrong" to 1))
                    }

                failure.key shouldBe "orders.count"
                failure.locale shouldBe Locale.ENGLISH
                failure.message!! shouldContain "needs count"
            }
        }

        feature("asking whether a message exists") {
            scenario("it follows the same walk the lookup does") {
                val french = messages.forLocale(Locale.FRENCH)

                ("orders.title" in french) shouldBe true
                ("only.in.english" in french) shouldBe true
                ("nobody.has.this" in french) shouldBe false
            }
        }
    })
