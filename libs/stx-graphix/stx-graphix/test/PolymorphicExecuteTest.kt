package com.strange.graphix

import com.strange.graphix.fixture.FilmOverrideFields
import com.strange.graphix.fixture.MediaFields
import com.strange.graphix.fixture.MediaQueries
import com.strange.graphix.fixture.StrayQueries
import com.strange.graphix.schema.typeResolver
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Resolving an abstract type at execute time, and what happens when it cannot be resolved. */
class PolymorphicExecuteTest :
    FeatureSpec({
        feature("abstract types at execute time") {
            scenario("an inline fragment selects the fields of the runtime type") {
                val graphql = Graphix { query(MediaQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest("{ search { ... on BookHit { title } ... on AuthorHit { name } } }"),
                    )

                result.isOk shouldBe true
                result.data?.get("search") shouldBe listOf(mapOf("title" to "Dune"), mapOf("name" to "Frank"))
            }

            scenario("__typename on a union is the runtime Kotlin class") {
                val graphql = Graphix { query(MediaQueries()) }
                val result = graphql.execute(GraphixRequest("{ search { __typename } }"))

                result.isOk shouldBe true
                result.data?.get("search") shouldBe
                    listOf(mapOf("__typename" to "BookHit"), mapOf("__typename" to "AuthorHit"))
            }

            scenario("__typename on an interface is the implementor, never the interface") {
                val graphql = Graphix { query(MediaQueries()) }
                val result = graphql.execute(GraphixRequest("{ media { __typename } }"))

                result.isOk shouldBe true
                result.data?.get("media") shouldBe
                    listOf(mapOf("__typename" to "Film"), mapOf("__typename" to "Song"))
            }

            scenario("interface fields are readable without a fragment, specific ones with one") {
                val graphql = Graphix { query(MediaQueries()) }
                val result =
                    graphql.execute(GraphixRequest("{ media { id title ... on Film { minutes } } }"))

                result.isOk shouldBe true
                val rows = result.data?.get("media") as List<*>
                (rows[0] as Map<*, *>)["minutes"] shouldBe 155
                (rows[1] as Map<*, *>)["title"] shouldBe "Ocean"
            }

            scenario("an interface-level @SchemaMapping runs for every implementor") {
                val graphql =
                    Graphix {
                        query(MediaQueries())
                        type(MediaFields())
                    }
                val result = graphql.execute(GraphixRequest("{ media { slug } }"))

                result.isOk shouldBe true
                result.data?.get("media") shouldBe listOf(mapOf("slug" to "dune"), mapOf("slug" to "ocean"))
            }

            scenario("a mapping on the implementor wins over the one it inherits") {
                val graphql =
                    Graphix {
                        query(MediaQueries())
                        type(FilmOverrideFields())
                        type(MediaFields())
                    }
                val result = graphql.execute(GraphixRequest("{ media { slug } }"))

                result.isOk shouldBe true
                result.data?.get("media") shouldBe listOf(mapOf("slug" to "film-f1"), mapOf("slug" to "ocean"))
            }
        }

        feature("type resolution") {
            scenario("a value the schema never heard of is a GraphQL error, not a crash") {
                val graphql = Graphix { query(StrayQueries()) }
                val result = graphql.execute(GraphixRequest("{ search { __typename } }"))

                result.isOk shouldBe false
                result.errors.first().message shouldContain "Graphix cannot resolve"
            }

            scenario("a typeResolver override wins over the class name") {
                val graphql =
                    Graphix {
                        typeResolver("SearchHit") { "AuthorHit" }
                        query(MediaQueries())
                    }
                val result = graphql.execute(GraphixRequest("{ search { __typename } }"))

                result.isOk shouldBe true
                result.data?.get("search") shouldBe
                    listOf(mapOf("__typename" to "AuthorHit"), mapOf("__typename" to "AuthorHit"))
            }
        }
    })
