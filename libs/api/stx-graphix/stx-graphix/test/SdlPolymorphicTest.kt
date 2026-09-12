package com.softistx.graphix

import com.softistx.graphix.fixture.SdlNodeFields
import com.softistx.graphix.fixture.SdlPolyQueries
import com.softistx.graphix.fixture.SdlUnionFields
import com.softistx.graphix.schema.typeResolver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * An SDL `union` or `interface` used to fail schema build outright: `SchemaGenerator` refuses a
 * document whose abstract types have no type resolver. These scenarios are that regression.
 */
class SdlPolymorphicTest :
    FeatureSpec({
        fun poly(block: GraphixBuilder.() -> Unit) =
            Graphix {
                schemaLocations("classpath:graphix-poly/")
                block()
            }

        feature("an SDL document with abstract types") {
            scenario("a union builds with no application type resolver") {
                val sdl = poly { resolvers(SdlPolyQueries()) }.sdl()

                sdl shouldContain "union SearchResult"
            }

            scenario("an interface builds with no application type resolver") {
                val sdl = poly { resolvers(SdlPolyQueries()) }.sdl()

                sdl shouldContain "interface Node"
                sdl shouldContain "type Film implements Node"
            }

            scenario("inline fragments select on a union") {
                val graphql = poly { resolvers(SdlPolyQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest("{ search { ... on Film { title minutes } ... on Song { bpm } } }"),
                    )

                result.isOk shouldBe true
                result.data?.get("search") shouldBe
                    listOf(mapOf("title" to "Dune", "minutes" to 155), mapOf("bpm" to 120))
            }

            scenario("__typename on a union is the runtime Kotlin class") {
                val graphql = poly { resolvers(SdlPolyQueries()) }
                val result = graphql.execute(GraphixRequest("{ search { __typename } }"))

                result.isOk shouldBe true
                result.data?.get("search") shouldBe
                    listOf(mapOf("__typename" to "Film"), mapOf("__typename" to "Song"))
            }

            scenario("a typeResolver override applies on the SDL path too") {
                val graphql =
                    poly {
                        typeResolver("SearchResult") { "Song" }
                        resolvers(SdlPolyQueries())
                    }
                val result = graphql.execute(GraphixRequest("{ search { __typename } }"))

                result.isOk shouldBe true
                result.data?.get("search") shouldBe
                    listOf(mapOf("__typename" to "Song"), mapOf("__typename" to "Song"))
            }

            scenario("an @SchemaMapping on an interface fans out to its implementors") {
                val graphql =
                    poly {
                        resolvers(SdlPolyQueries())
                        resolvers(SdlNodeFields())
                    }
                val result = graphql.execute(GraphixRequest("{ nodes { id slug } }"))

                result.isOk shouldBe true
                result.data?.get("nodes") shouldBe listOf(mapOf("id" to "f1", "slug" to "dune"))
            }

            scenario("an @SchemaMapping on a union fails naming what a union is") {
                val failure =
                    shouldThrow<GraphixException> {
                        poly {
                            resolvers(SdlPolyQueries())
                            resolvers(SdlUnionFields())
                        }
                    }

                failure.message shouldContain "a GraphQL union has no fields"
            }
        }
    })
