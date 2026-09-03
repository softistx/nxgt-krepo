package com.softistx.graphix

import com.softistx.graphix.fixture.ExtendedScalarQueries
import com.softistx.graphix.fixture.QuantityQueries
import com.softistx.graphix.fixture.ScalarQueries
import com.softistx.graphix.scalar.Scalars
import com.softistx.graphix.scalar.scalar
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.math.BigDecimal
import java.math.BigInteger

class ExtendedScalarTest :
    FeatureSpec({
        val graphix = Graphix { query(ExtendedScalarQueries()) }

        feature("extended scalars") {
            scenario("every one round-trips through a literal") {
                val result =
                    graphix.execute(
                        GraphixRequest(
                            """
                            {
                              date(value: "2026-09-02")
                              time(value: "14:30:05")
                              timestamp(value: "2026-09-02T14:30:05")
                              lasts(value: "PT1H30M")
                              amount(value: "19.99")
                              huge(value: 170141183460469231731687303715884105727)
                              tiny(value: 42)
                              octet(value: 7)
                              letter(value: "A")
                              link(value: "https://example.test/a")
                              language(value: "fr-CA")
                              payload(value: { note: "hi", count: 2 })
                            }
                            """.trimIndent(),
                        ),
                    )
                result.errors shouldBe emptyList()
                result.data shouldBe
                    mapOf(
                        "date" to "2026-09-02",
                        "time" to "14:30:05",
                        "timestamp" to "2026-09-02T14:30:05",
                        "lasts" to "PT1H30M",
                        "amount" to BigDecimal("19.99"),
                        "huge" to BigInteger("170141183460469231731687303715884105727"),
                        "tiny" to 42.toShort(),
                        "octet" to 7.toByte(),
                        "letter" to "A",
                        "link" to "https://example.test/a",
                        "language" to "fr-CA",
                        "payload" to mapOf("note" to "hi", "count" to 2L),
                    )
            }

            scenario("a variable takes the same values as a literal") {
                val result =
                    graphix.execute(
                        GraphixRequest(
                            "query Echo(\$d: LocalDate!, \$p: Json!) { date(value: \$d) payload(value: \$p) }",
                            variables = mapOf("d" to "2026-09-02", "p" to mapOf("note" to "hi")),
                        ),
                    )
                result.errors shouldBe emptyList()
                result.data shouldBe mapOf("date" to "2026-09-02", "payload" to mapOf("note" to "hi"))
            }

            scenario("an argument arrives as its Kotlin type, not as JSON that was decoded twice") {
                // BigDecimal has no KSerializer. Reaching the resolver at all is the assertion.
                val result = graphix.execute(GraphixRequest("""{ amount(value: "0.1") }"""))
                result.errors shouldBe emptyList()
                result.data shouldBe mapOf("amount" to BigDecimal("0.1"))
            }

            scenario("a value the scalar cannot parse is a coercion error, not a null field") {
                val result = graphix.execute(GraphixRequest("""{ date(value: "the 2nd") }"""))
                result.isOk shouldBe false
                result.errors.single().message shouldContain "LocalDate"
            }

            scenario("an integral scalar refuses a value outside its width") {
                val result = graphix.execute(GraphixRequest("{ octet(value: 300) }"))
                result.isOk shouldBe false
                result.errors.single().message shouldContain "-128"
            }

            scenario("a Url has to be absolute") {
                val result = graphix.execute(GraphixRequest("""{ link(value: "../thumb.png") }"""))
                result.isOk shouldBe false
                result.errors.single().message shouldContain "Url"
            }
        }

        feature("what the schema advertises") {
            scenario("every built-in is there by default, used or not") {
                val small = Graphix { query(ScalarQueries()) }
                Scalars.All.forEach { small.sdl() shouldContain "scalar ${it.name}" }
            }

            scenario("builtInScalars(false) narrows it to what a field resolved to") {
                val small =
                    Graphix {
                        builtInScalars(false)
                        query(ScalarQueries())
                    }
                small.sdl() shouldContain "scalar Instant"
                small.sdl() shouldNotContain "scalar LocalDate"
                small.sdl() shouldNotContain "scalar PositiveInt"
            }

            scenario("a scalar the application defined under a built-in's name stays its own") {
                val engine =
                    Graphix {
                        scalar("Locale", description = "ours") {
                            serialize { value -> value.toString() }
                        }
                        query(ScalarQueries())
                    }
                engine.sdl() shouldContain "\"ours\"\nscalar Locale"
            }
        }

        feature("bounded scalars") {
            val bounded =
                Graphix {
                    schemaLocations("graphix-bounded")
                    query(QuantityQueries())
                }

            scenario("an SDL document declares one and needs no wiring") {
                val result = bounded.execute(GraphixRequest("{ quantity(value: 5) }"))
                result.errors shouldBe emptyList()
                result.data shouldBe mapOf("quantity" to 5)
            }

            scenario("the range is enforced before the resolver runs") {
                val result = bounded.execute(GraphixRequest("{ quantity(value: 0) }"))
                result.isOk shouldBe false
                result.errors.single().message shouldContain "greater than zero"
            }

            scenario("a document gets the built-ins it never declared") {
                // bounded.graphqls names PositiveInt and nothing else.
                bounded.sdl() shouldContain "scalar LocalDate"
                bounded.sdl() shouldContain "scalar BigDecimal"
            }

            scenario("builtInScalars(false) leaves the document exactly as it was written") {
                val narrow =
                    Graphix {
                        builtInScalars(false)
                        schemaLocations("graphix-bounded")
                        query(QuantityQueries())
                    }
                narrow.sdl() shouldContain "scalar PositiveInt"
                narrow.sdl() shouldNotContain "scalar LocalDate"
                // The wiring is unconditional, so the document's own declaration still resolves.
                narrow.execute(GraphixRequest("{ quantity(value: 5) }")).data shouldBe mapOf("quantity" to 5)
            }
        }
    })
