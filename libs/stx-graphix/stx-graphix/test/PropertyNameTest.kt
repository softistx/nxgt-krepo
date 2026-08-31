package com.strange.graphix

import com.strange.graphix.fixture.RecordQueries
import com.strange.graphix.fixture.SlippedQueries
import com.strange.graphix.fixture.StampedQueries
import com.strange.graphix.fixture.TrackQueries
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * A property whose GraphQL name is not its Kotlin name. `@SerialName` is the only thing that
 * renames one, because the SerialDescriptor **is** the type system here. graphql-java's default
 * fetcher would look for the GraphQL name on the Kotlin object and find nothing, so the field gets
 * a fetcher that knows both names.
 */
class PropertyNameTest :
    FeatureSpec({
        feature("@SerialName on an object type") {
            scenario("the GraphQL field is the serial name, not the Kotlin name") {
                val sdl = Graphix { query(TrackQueries()) }.sdl()

                sdl shouldContain "track_id: String!"
                sdl.substringAfter("type Track").substringBefore("}") shouldNotContain " id:"
            }

            scenario("and it resolves through the property it renamed") {
                val graphql = Graphix { query(TrackQueries()) }
                val result = graphql.execute(GraphixRequest("{ track { track_id title } }"))

                result.isOk shouldBe true
                result.data?.get("track") shouldBe mapOf("track_id" to "t1", "title" to "Ocean")
            }

            scenario("an input object decodes by the same name the schema advertises") {
                val graphql = Graphix { query(TrackQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest("""{ echo(filter: { track_id: "t9" }) }"""),
                    )

                result.isOk shouldBe true
                result.data?.get("echo") shouldBe "t9"
            }
        }

        feature("@GraphQLName names a type, and only a type") {
            scenario("the object type is the annotation's name, in the schema and in __typename") {
                val graphql = Graphix { query(RecordQueries()) }

                graphql.sdl() shouldContain "type Vinyl"
                graphql.sdl() shouldNotContain "type Record"
                val result = graphql.execute(GraphixRequest("{ record { label __typename } }"))

                result.isOk shouldBe true
                result.data?.get("record") shouldBe mapOf("label" to "Blue Note", "__typename" to "Vinyl")
            }
        }

        feature("@SerialName on an interface's shared property") {
            scenario("the interface and its implementor agree on the field name") {
                val graphql = Graphix { query(StampedQueries()) }

                graphql.sdl() shouldContain "type Receipt implements Stamped"
                graphql.sdl() shouldContain "stamped_at: String!"
                val result = graphql.execute(GraphixRequest("{ stamped { stamped_at } }"))
                result.data?.get("stamped") shouldBe mapOf("stamped_at" to "2026-08-31")
            }

            scenario("an implementor that drops the rename fails schema build naming both") {
                val failure = shouldThrow<GraphixException> { Graphix { query(SlippedQueries()) } }

                failure.message shouldContain "Missed.at is 'at'"
                failure.message shouldContain "'slipped_at'"
            }
        }
    })
