package com.softistx.graphix

import com.softistx.graphix.fixture.BadPickQueries
import com.softistx.graphix.fixture.DefaultedDeprecatedQueries
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.RequiredDeprecatedQueries
import com.softistx.graphix.fixture.SpecQueries
import com.softistx.graphix.scalar.scalar
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The spec directives a schema may carry: `@deprecated`, `@specifiedBy` and `@oneOf`. */
class SpecDirectiveTest :
    FeatureSpec({
        feature("@deprecated") {
            scenario("a deprecated field is in introspection with its reason, and still resolves") {
                val graphql = Graphix { query(SpecQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest(
                            """{ __type(name: "Query") { fields(includeDeprecated: true) { name isDeprecated deprecationReason } } }""",
                        ),
                    )

                val fields = (result.data?.get("__type") as Map<*, *>)["fields"] as List<*>
                val hail = fields.map { it as Map<*, *> }.first { it["name"] == "hail" }
                hail["isDeprecated"] shouldBe true
                hail["deprecationReason"] shouldBe "use greet"

                graphql.execute(GraphixRequest("{ hail }")).data shouldBe mapOf("hail" to "hail")
            }

            scenario("a deprecated field is hidden from introspection by default") {
                val graphql = Graphix { query(SpecQueries()) }
                val result = graphql.execute(GraphixRequest("""{ __type(name: "Query") { fields { name } } }"""))

                val names = ((result.data?.get("__type") as Map<*, *>)["fields"] as List<*>).map { (it as Map<*, *>)["name"] }
                names.contains("hail") shouldBe false
                names.contains("greet") shouldBe true
            }

            scenario("an optional argument and an input field may be deprecated") {
                val graphql = Graphix { query(SpecQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest(
                            """{ __type(name: "LegacyInput") { inputFields(includeDeprecated: true) { name isDeprecated } } }""",
                        ),
                    )

                val fields = ((result.data?.get("__type") as Map<*, *>)["inputFields"] as List<*>).map { it as Map<*, *> }
                fields.first { it["name"] == "label" }["isDeprecated"] shouldBe true
                fields.first { it["name"] == "name" }["isDeprecated"] shouldBe false
            }

            scenario("a required argument may not be deprecated") {
                val failure = shouldThrow<GraphixException> { Graphix { query(RequiredDeprecatedQueries()) } }

                failure.message shouldContain "cannot be @GraphQLDeprecated"
            }

            scenario("a non-null argument with a default may be deprecated — omitting it still works") {
                val graphql = Graphix { query(DefaultedDeprecatedQueries()) }

                graphql.sdl() shouldContain "limit: Int! = 10 @deprecated(reason : \"use cursor\")"
                graphql.execute(GraphixRequest("{ page }")).data?.get("page") shouldBe 10
            }
        }

        feature("@specifiedBy") {
            scenario("a scalar's specification URL reaches introspection") {
                val graphql =
                    Graphix {
                        scalar("Money", specifiedBy = "https://example.test/money") {
                            serialize { it }
                            parseValue { it }
                        }
                        query(GreetingQueries())
                    }
                val result = graphql.execute(GraphixRequest("""{ __type(name: "Money") { specifiedByURL } }"""))

                result.isOk shouldBe true
                (result.data?.get("__type") as Map<*, *>)["specifiedByURL"] shouldBe "https://example.test/money"
            }
        }

        feature("@oneOf") {
            scenario("a @GraphQLOneOf input object is marked oneOf in introspection") {
                val graphql = Graphix { query(SpecQueries()) }
                val result = graphql.execute(GraphixRequest("""{ __type(name: "PickInput") { isOneOf } }"""))

                result.isOk shouldBe true
                (result.data?.get("__type") as Map<*, *>)["isOneOf"] shouldBe true
            }

            scenario("exactly one field is accepted") {
                val graphql = Graphix { query(SpecQueries()) }
                val result = graphql.execute(GraphixRequest("""{ pick(input: { byId: "p1" }) }"""))

                result.isOk shouldBe true
                result.data shouldBe mapOf("pick" to "p1")
            }

            scenario("two fields are rejected") {
                val graphql = Graphix { query(SpecQueries()) }
                val result = graphql.execute(GraphixRequest("""{ pick(input: { byId: "p1", byName: "Mug" }) }"""))

                result.isOk shouldBe false
            }

            scenario("no field at all is rejected") {
                val graphql = Graphix { query(SpecQueries()) }
                val result = graphql.execute(GraphixRequest("{ pick(input: {}) }"))

                result.isOk shouldBe false
            }

            scenario("the one field may not be null") {
                val graphql = Graphix { query(SpecQueries()) }
                val result = graphql.execute(GraphixRequest("{ pick(input: { byId: null }) }"))

                result.isOk shouldBe false
            }

            scenario("a non-nullable field fails schema build, naming it") {
                val failure = shouldThrow<GraphixException> { Graphix { query(BadPickQueries()) } }

                failure.message shouldContain "is not nullable"
                failure.message shouldContain "byId"
            }

            scenario("an SDL @oneOf input is enforced the same way") {
                val graphql =
                    Graphix {
                        schemaLocations("classpath:graphix-oneof/")
                        query(SpecQueries())
                    }

                graphql.execute(GraphixRequest("""{ pick(input: { byName: "Mug" }) }""")).data shouldBe
                    mapOf("pick" to "Mug")
                graphql.execute(GraphixRequest("""{ pick(input: { byId: "p1", byName: "Mug" }) }""")).isOk shouldBe false
            }
        }
    })
