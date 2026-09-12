package com.softistx.graphix

import com.softistx.graphix.error.errors
import com.softistx.graphix.error.on
import com.softistx.graphix.error.withMessage
import com.softistx.graphix.fixture.ProductQueries
import com.softistx.graphix.fixture.StagedStreamQueries
import com.softistx.graphix.fixture.StreamProductFields
import com.softistx.graphix.fixture.StreamQueries
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.withTimeout
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * A `Flow` where the schema says a list.
 *
 * `FlowSeatTest` measured what happened before: the annotation path failed at schema build advising
 * a sealed interface, and the SDL path built fine and then failed at request time with graphql-java's
 * `expected type LIST`. Both are what these scenarios replace.
 *
 * The thing to keep straight is that `Flow` means two different things, and the annotation decides
 * which: a stream of separate responses under `@SubscriptionMapping`, a list inside one response
 * everywhere else. `SubscriptionTest` still owns the first.
 */
class StreamListTest :
    FeatureSpec({
        feature("a stream where the schema says a list") {
            scenario("a plain fun returning Flow<T> is the list — the case that started this") {
                val graphql = Graphix { resolvers(StreamQueries()) }

                graphql.sdl() shouldContain "names: [String!]!"
                graphql.execute(GraphixRequest("{ names }")).data?.get("names") shouldBe listOf("ada", "grace")
            }

            scenario("a suspend resolver's Flow collects the same way") {
                Graphix { resolvers(StreamQueries()) }
                    .execute(GraphixRequest("{ suspendedNames }"))
                    .data
                    ?.get("suspendedNames") shouldBe listOf("ada", "grace")
            }

            scenario("a reactive-streams Publisher does too") {
                Graphix { resolvers(StreamQueries()) }
                    .execute(GraphixRequest("{ published }"))
                    .data
                    ?.get("published") shouldBe listOf("ada", "grace")
            }

            scenario("and a JDK Flow.Publisher, through the bridge reactive-streams already ships") {
                Graphix { resolvers(StreamQueries()) }
                    .execute(GraphixRequest("{ jdkPublished }"))
                    .data
                    ?.get("jdkPublished") shouldBe listOf("ada", "grace")
            }

            scenario("an empty Flow is an empty list, not null") {
                Graphix { resolvers(StreamQueries()) }
                    .execute(GraphixRequest("{ empty }"))
                    .data
                    ?.get("empty") shouldBe emptyList<String>()
            }

            scenario("Flow<T>? is a nullable list, and returning null stays null") {
                val graphql = Graphix { resolvers(StreamQueries()) }

                graphql.sdl() shouldContain "missing: [String!]"
                graphql.execute(GraphixRequest("{ missing }")).data?.get("missing") shouldBe null
            }

            scenario("Flow<T?> keeps the element nullable, and a null element survives") {
                val graphql = Graphix { resolvers(StreamQueries()) }

                graphql.sdl() shouldContain "sparse: [String]!"
                graphql.execute(GraphixRequest("{ sparse }")).data?.get("sparse") shouldBe listOf("ada", null)
            }

            scenario("@GraphQLId carries down to the element, as it does for a List") {
                // The rewrite to List<T> is what buys this: idScalar walks through List::class and
                // would otherwise refuse a Flow naming it as a type ID cannot be.
                Graphix { resolvers(StreamQueries()) }.sdl() shouldContain "ids: [ID!]!"
            }

            scenario("a Flow of objects is a list of them, selectable field by field") {
                val data = Graphix { resolvers(StreamQueries()) }.execute(GraphixRequest("{ objects { id name } }")).data
                data?.get("objects") shouldBe listOf(mapOf("id" to "p1", "name" to "Mug"))
            }

            scenario("a @SchemaMapping type field collects too") {
                val graphql = Graphix { resolvers(ProductQueries(), StreamProductFields()) }

                graphql.sdl() shouldContain "aliases: [String!]!"
                val data = graphql.execute(GraphixRequest("{ products { aliases } }")).data
                data?.get("products") shouldBe listOf(mapOf("aliases" to listOf("Mug-1", "Mug-2")))
            }

            scenario("an SDL list field is served by a Flow-returning resolver") {
                // The oauth case exactly: the document owns the type, the Kotlin return type is read
                // by nobody, and the fix is entirely in the fetcher.
                val dir = createTempDirectory("graphix-stream")
                dir.resolve("names.graphqls").writeText("type Query { names: [String!]! }")

                Graphix {
                    schemaLocations("file:${dir.toAbsolutePath()}")
                    resolvers(StreamQueries())
                }.execute(GraphixRequest("{ names }"))
                    .data
                    ?.get("names") shouldBe listOf("ada", "grace")
            }
        }

        feature("what a stream return still refuses") {
            scenario("CompletionStage<Flow<T>> is refused at schema build, naming both") {
                val failure = shouldThrow<GraphixException> { Graphix { resolvers(StagedStreamQueries()) } }

                failure.message shouldContain "not CompletionStage<Flow<T>>"
            }
        }

        feature("maxListElements") {
            scenario("unset, nothing is bounded — a List return never was either") {
                Graphix { resolvers(StreamQueries()) }
                    .execute(GraphixRequest("{ counted(to: 5000) }"))
                    .data
                    ?.get("counted")
                    .let { it as List<*> }
                    .size shouldBe 5000
            }

            scenario("past the bound the field is an error naming it") {
                val graphql =
                    Graphix {
                        resolvers(StreamQueries())
                        maxListElements(3)
                    }
                val error = graphql.execute(GraphixRequest("{ counted(to: 10) }")).errors.single()

                error.message shouldContain "'counted' produced more than 3 elements"
                error.path shouldBe listOf("counted")
            }

            scenario("exactly the bound is fine — it is a maximum, not a limit to stay under") {
                Graphix {
                    resolvers(StreamQueries())
                    maxListElements(3)
                }.execute(GraphixRequest("{ counted(to: 3) }"))
                    .data
                    ?.get("counted") shouldBe listOf(0, 1, 2)
            }

            scenario("an infinite Flow ends the request instead of hanging it") {
                val graphql =
                    Graphix {
                        resolvers(StreamQueries())
                        maxListElements(10)
                    }
                // The timeout is the assertion. A bound that reported without cancelling the source
                // would leave this collecting forever, and a regression must fail rather than wedge
                // the suite.
                withTimeout(10_000) {
                    graphql
                        .execute(GraphixRequest("{ endless }"))
                        .errors
                        .single()
                        .message shouldContain "more than 10 elements"
                }
            }

            scenario("it is an ordinary field error, so an errors { } handler can edit it") {
                val graphql =
                    Graphix {
                        resolvers(StreamQueries())
                        maxListElements(2)
                        errors { on<TooManyElements> { failure -> error.withMessage("too much on ${failure.field}") } }
                    }
                graphql
                    .execute(GraphixRequest("{ counted(to: 9) }"))
                    .errors
                    .single()
                    .message shouldBe "too much on counted"
            }

            scenario("a resolver already returning a List is untouched by the bound") {
                Graphix {
                    resolvers(ProductQueries())
                    maxListElements(1)
                }.execute(GraphixRequest("{ sizes }"))
                    .data
                    ?.get("sizes") shouldBe listOf("S", "M", "L")
            }

            scenario("zero or less is refused where it is written") {
                shouldThrow<IllegalArgumentException> {
                    Graphix {
                        resolvers(StreamQueries())
                        maxListElements(0)
                    }
                }
            }
        }
    })
