package com.strange.i18n

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

/**
 * Picking a locale from what the browser asked for.
 *
 * This is the piece the Android implementation had no need for — there, the locale is a setting.
 * On a server it arrives on every request as a ranked list, from a client nobody controls, and both
 * halves of that matter: the ranking has to be honoured, and a malformed header must not fail a
 * request.
 */
class NegotiationTest :
    FeatureSpec({

        val messages =
            Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH, Locale.CANADA_FRENCH))

        feature("an Accept-Language header") {
            scenario("the most specific shipped match wins") {
                messages.negotiate("fr-CA,fr;q=0.9,en;q=0.8").locale shouldBe Locale.CANADA_FRENCH
            }

            scenario("a region nobody ships truncates to its language rather than giving up") {
                // fr-BE is not shipped; French is, and is a far better answer than English.
                messages.negotiate("fr-BE,fr;q=0.9").locale shouldBe Locale.FRENCH
            }

            scenario("quality values decide, not the order they happen to be written in") {
                messages.negotiate("fr;q=0.2,en;q=0.9").locale shouldBe Locale.ENGLISH
            }

            scenario("a language nobody ships falls back") {
                messages.negotiate("de-DE,de;q=0.9").locale shouldBe Locale.ENGLISH
            }
        }

        feature("headers a client sends that nobody planned for") {
            scenario("absent, empty or malformed, the fallback answers rather than an exception") {
                /* The header is user input. A 400 because somebody's extension sent nonsense in
                   Accept-Language would be this library's fault, not theirs. */
                messages.negotiate(null).locale shouldBe Locale.ENGLISH
                messages.negotiate("").locale shouldBe Locale.ENGLISH
                messages.negotiate("   ").locale shouldBe Locale.ENGLISH
                messages.negotiate("fr;q=not-a-number").locale shouldBe Locale.ENGLISH
                messages.negotiate("*").locale shouldBe Locale.ENGLISH
            }
        }

        feature("what a negotiated view then answers") {
            scenario("it is a translator like any other, chain and all") {
                val quebec = messages.negotiate("fr-CA")

                quebec["checkout.button"] shouldBe "Passer la commande maintenant"
                quebec["orders.title"] shouldBe "Commandes"
                quebec["only.in.english"] shouldBe "Nobody has translated this yet"
            }
        }
    })
