package com.softistx.graphix

import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.ProductQueries
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * What the GraphQL document may say, independent of the schema. None of this is Graphix code —
 * `@skip`, `@include`, fragments, aliases, variables and `__typename` are the engine's — which is
 * exactly why it is asserted here: nothing else in the suite would notice if a change to the
 * execution input, the argument binding or the result mapping broke one of them.
 */
class ConformanceTest :
    FeatureSpec({
        feature("built-in directives") {
            scenario("@skip drops the field when its condition is true") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val query = "query Q(\$hide: Boolean!) { hello @skip(if: \$hide) }"

                val skipped = graphql.execute(GraphixRequest(query, mapOf("hide" to true)))
                skipped.isOk shouldBe true
                skipped.data.shouldNotBeNull().keys shouldNotContain "hello"

                val kept = graphql.execute(GraphixRequest(query, mapOf("hide" to false)))
                kept.data shouldBe mapOf("hello" to "world")
            }

            scenario("@include keeps the field only when its condition is true") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val query = "query Q(\$show: Boolean!) { hello @include(if: \$show) }"

                graphql.execute(GraphixRequest(query, mapOf("show" to true))).data shouldBe mapOf("hello" to "world")
                graphql
                    .execute(GraphixRequest(query, mapOf("show" to false)))
                    .data
                    .shouldNotBeNull()
                    .keys shouldNotContain "hello"
            }
        }

        feature("selection sets") {
            scenario("a named fragment expands into the selection") {
                val graphql = Graphix { resolvers(ProductQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest(
                            """
                            { product(id: "p1") { ...fields } }
                            fragment fields on Product { name tags }
                            """.trimIndent(),
                        ),
                    )

                result.isOk shouldBe true
                val product = result.data?.get("product") as Map<*, *>
                product["name"] shouldBe "Mug"
                product["tags"] shouldBe listOf("kitchen")
            }

            scenario("an inline fragment on the field's own type expands too") {
                val graphql = Graphix { resolvers(ProductQueries()) }
                val result = graphql.execute(GraphixRequest("""{ product(id: "p1") { ... on Product { name } } }"""))

                result.isOk shouldBe true
                (result.data?.get("product") as Map<*, *>)["name"] shouldBe "Mug"
            }

            scenario("__typename is the GraphQL type name, at the root and on a field") {
                val graphql = Graphix { resolvers(ProductQueries()) }
                val result = graphql.execute(GraphixRequest("""{ __typename product(id: "p1") { __typename } }"""))

                result.isOk shouldBe true
                result.data?.get("__typename") shouldBe "Query"
                (result.data?.get("product") as Map<*, *>)["__typename"] shouldBe "Product"
            }

            scenario("two aliases of one field with different arguments each keep their own value") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val result =
                    graphql.execute(GraphixRequest("""{ ada: shout(name: "ada") grace: shout(name: "grace") }"""))

                result.isOk shouldBe true
                result.data shouldBe mapOf("ada" to "ADA", "grace" to "GRACE")
            }
        }

        feature("variables and operations") {
            scenario("operationName picks one operation out of a document that holds two") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val document =
                    """
                    query Greet { hello }
                    query Shout { shout(name: "ada") }
                    """.trimIndent()

                graphql.execute(GraphixRequest(document, operationName = "Greet")).data shouldBe mapOf("hello" to "world")
                graphql.execute(GraphixRequest(document, operationName = "Shout")).data shouldBe mapOf("shout" to "ADA")
            }

            scenario("a variable's own default value applies when the request omits it") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val result = graphql.execute(GraphixRequest("query Q(\$n: String = \"ada\") { shout(name: \$n) }"))

                result.isOk shouldBe true
                result.data shouldBe mapOf("shout" to "ADA")
            }

            scenario("an explicit null variable falls through to the Kotlin default") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest("query Q(\$n: String) { shout(name: \$n) }", mapOf("n" to null)),
                    )

                result.isOk shouldBe true
                result.data shouldBe mapOf("shout" to "STRANGER")
            }

            scenario("a variable of the wrong type is a GraphQL error, not an exception") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest("query Q(\$n: String) { shout(name: \$n) }", mapOf("n" to listOf("ada"))),
                    )

                result.isOk shouldBe false
                result.data shouldBe null
            }
        }

        feature("introspection meta-fields") {
            scenario("__type answers for a type the document never selects") {
                val graphql = Graphix { resolvers(ProductQueries()) }
                val result = graphql.execute(GraphixRequest("""{ __type(name: "Product") { name kind } }"""))

                result.isOk shouldBe true
                val type = result.data?.get("__type") as Map<*, *>
                type["name"] shouldBe "Product"
                type["kind"] shouldBe "OBJECT"
            }

            scenario("__schema names the query root") {
                val graphql = Graphix { resolvers(ProductQueries()) }
                val result = graphql.execute(GraphixRequest("{ __schema { queryType { name } } }"))

                result.isOk shouldBe true
                result.data.shouldNotBeNull().keys shouldContain "__schema"
            }
        }
    })
