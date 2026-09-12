package com.softistx.graphix

import com.softistx.graphix.fixture.ExtendedScalarQueries
import com.softistx.graphix.http.acceptedLocale
import com.softistx.graphix.message.GraphixMessages
import com.softistx.graphix.message.MessageKeys
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.util.Locale

/** A bad **literal**: coerced during validation. */
private val badDate = GraphixRequest("""{ date(value: "the 2nd") }""")

/** The same bad value as a **variable**: coerced during execution, with the operation's context. */
private val badVariable =
    GraphixRequest(
        "query Echo(\$d: LocalDate!) { date(value: \$d) }",
        variables = mapOf("d" to "the 2nd"),
    )

class ScalarMessageTest :
    FeatureSpec({
        val graphix = Graphix { resolvers(ExtendedScalarQueries()) }

        feature("coercion errors are translated") {
            scenario("the operation's locale decides the language") {
                val english = graphix.execute(badDate.copy(locale = Locale.ENGLISH))
                val french = graphix.execute(badDate.copy(locale = Locale.FRENCH))

                english.errors.single().message shouldContain "cannot parse"
                french.errors.single().message shouldContain "ne peut pas analyser"
            }

            scenario("a regional locale falls back to its language") {
                val result = graphix.execute(badDate.copy(locale = Locale.CANADA_FRENCH))
                result.errors.single().message shouldContain "ne peut pas analyser"
            }

            scenario("a locale nothing was written for falls back to the base catalogue") {
                val result = graphix.execute(badDate.copy(locale = Locale.forLanguageTag("ja")))
                result.errors.single().message shouldContain "cannot parse"
            }

            scenario("the scalar's name is in the message, so one key serves every scalar") {
                val result = graphix.execute(badDate.copy(locale = Locale.FRENCH))
                result.errors.single().message shouldContain "LocalDate"
            }
        }

        feature("where the text comes from") {
            scenario("a message source declared on the builder replaces the bundled one") {
                val engine =
                    Graphix {
                        messages { _, key, args -> "$key/${args["scalar"]}" }
                        resolvers(ExtendedScalarQueries())
                    }
                // graphql-java wraps it in "Variable 'd' has an invalid value: ", its own text.
                engine
                    .execute(badVariable)
                    .errors
                    .single()
                    .message shouldContain "${MessageKeys.PARSE_REASON}/LocalDate"
            }

            scenario("one operation may override it through the context map") {
                val engine =
                    Graphix {
                        messages { _, _, _ -> "builder" }
                        resolvers(ExtendedScalarQueries())
                    }
                val messages = GraphixMessages { _, _, _ -> "operation" }
                val result = engine.execute(badVariable, mapOf(GraphixMessages::class to messages))
                result.errors.single().message shouldContain "operation"
            }

            scenario("a literal is coerced by the validator, which carries only the locale") {
                /* graphql-java's ValidationContext builds its own GraphQLContext holding
                   Locale and nothing else, so a source put in the operation's context is not
                   there to be found. The locale still is — which is the half that has to work. */
                val engine =
                    Graphix {
                        messages { _, _, _ -> "builder" }
                        resolvers(ExtendedScalarQueries())
                    }
                engine
                    .execute(badDate.copy(locale = Locale.FRENCH))
                    .errors
                    .single()
                    .message shouldContain
                    "ne peut pas analyser"
            }
        }

        feature("the bundled catalogues") {
            scenario("English answers every key the scalars look up") {
                MessageKeys.All.forEach { key ->
                    GraphixMessages.Bundled.message(Locale.ENGLISH, key, emptyMap()) shouldNotBe key
                }
            }

            scenario("French answers every one of them too") {
                MessageKeys.All.forEach { key ->
                    val french = GraphixMessages.Bundled.message(Locale.FRENCH, key, emptyMap())
                    french shouldNotBe key
                    french shouldNotBe GraphixMessages.Bundled.message(Locale.ENGLISH, key, emptyMap())
                }
            }

            scenario("a key nobody wrote is the key, not a blank message") {
                GraphixMessages.Bundled.message(Locale.ENGLISH, "stx.graphix.messages.nothing", emptyMap()) shouldBe
                    "stx.graphix.messages.nothing"
            }
        }

        feature("Accept-Language") {
            scenario("the highest-weighted tag wins, whatever order it was written in") {
                acceptedLocale("en;q=0.8, fr-CA;q=0.9") shouldBe Locale.forLanguageTag("fr-CA")
            }

            scenario("a header nobody can parse is no locale, not a failed request") {
                acceptedLocale("¯\\_(ツ)_/¯") shouldBe null
                acceptedLocale("") shouldBe null
                acceptedLocale(null) shouldBe null
            }
        }
    })
