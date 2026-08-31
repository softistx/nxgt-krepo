package com.strange.graphix

import com.strange.graphix.fixture.ChoiceQueries
import com.strange.graphix.fixture.EmptyQueries
import com.strange.graphix.fixture.MediaFields
import com.strange.graphix.fixture.MediaQueries
import com.strange.graphix.fixture.RenamedQueries
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/** What a sealed Kotlin hierarchy becomes in the schema. */
class PolymorphicSchemaTest :
    FeatureSpec({
        feature("sealed hierarchies") {
            scenario("a sealed interface with no shared property becomes a union") {
                val sdl = Graphix { query(MediaQueries()) }.sdl()

                sdl shouldContain "union SearchHit = AuthorHit | BookHit"
                sdl shouldContain "type BookHit"
                sdl shouldContain "type AuthorHit"
                sdl shouldNotContain "interface SearchHit"
            }

            scenario("a sealed interface with shared properties becomes an interface") {
                val sdl = Graphix { query(MediaQueries()) }.sdl()

                sdl shouldContain "interface Media"
                sdl shouldContain "type Film implements Media"
                sdl shouldContain "type Song implements Media"
            }

            scenario("@GraphQLUnion forces a union on a sealed type that has shared properties") {
                val sdl = Graphix { query(MediaQueries()) }.sdl()

                sdl shouldContain "union Payload"
                sdl shouldNotContain "interface Payload"
            }

            scenario("a nested sealed level is Kotlin structure, not a member type") {
                val sdl = Graphix { query(MediaQueries()) }.sdl()

                sdl shouldContain "union Node = Folder | Leaf"
                sdl shouldNotContain "Container"
            }

            scenario("union members are in the schema even though no field returns them directly") {
                val graphql = Graphix { query(MediaQueries()) }
                val result = graphql.execute(GraphixRequest("""{ __type(name: "BookHit") { name kind } }"""))

                result.isOk shouldBe true
                (result.data?.get("__type") as Map<*, *>)["kind"] shouldBe "OBJECT"
            }

            scenario("an @SchemaMapping on an interface lands on the interface and on every implementor") {
                val sdl =
                    Graphix {
                        query(MediaQueries())
                        type(MediaFields())
                    }.sdl()

                sdl shouldContain "slug: String!"
                sdl.substringAfter("type Film implements Media").substringBefore("}") shouldContain "slug"
                sdl.substringAfter("type Song implements Media").substringBefore("}") shouldContain "slug"
            }
        }

        feature("what a sealed type may not be") {
            scenario("a sealed type as an argument fails naming input unions") {
                val failure = shouldThrow<GraphixException> { Graphix { query(ChoiceQueries()) } }

                failure.message shouldContain "GraphQL has no input unions"
            }

            scenario("a member with no fields fails naming the Kotlin object") {
                val failure = shouldThrow<GraphixException> { Graphix { query(EmptyQueries()) } }

                failure.message shouldContain "has no GraphQL fields"
                failure.message shouldContain "Nothing"
            }

            scenario("an implementor may not rename a field its interface declares") {
                val failure = shouldThrow<GraphixException> { Graphix { query(RenamedQueries()) } }

                failure.message shouldContain "must keep the interface's field name"
            }
        }
    })
