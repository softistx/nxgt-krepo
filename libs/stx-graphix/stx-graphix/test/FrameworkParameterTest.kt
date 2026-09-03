package com.softistx.graphix

import com.softistx.graphix.fixture.BadgeBatch
import com.softistx.graphix.fixture.BadgeContextBatch
import com.softistx.graphix.fixture.BadgeFields
import com.softistx.graphix.fixture.Caller
import com.softistx.graphix.fixture.CallerSubscriptions
import com.softistx.graphix.fixture.ContextBagQueries
import com.softistx.graphix.fixture.FakeCall
import com.softistx.graphix.fixture.FrameworkParameterQueries
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.UnregisteredQueries
import com.softistx.graphix.schema.contextParameter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.toList

/**
 * A resolver parameter the framework supplies rather than the document.
 *
 * [FakeCall] stands in for `ApplicationCall` and `ServerWebExchange`: the core names neither, so
 * what is pinned here is the registration seam itself — that `contextParameter(...)` is enough to
 * make an arbitrary type a parameter, and that forgetting it still fails the way a forgotten
 * `@Argument` always has.
 */
class FrameworkParameterTest :
    FeatureSpec({

        val call = FakeCall(mapOf("X-User" to "ada"))

        feature("a registered type") {
            scenario("binds from the operation context, with no annotation") {
                val graphix =
                    Graphix {
                        resolvers(FrameworkParameterQueries())
                        contextParameter(FakeCall::class)
                    }
                val result = graphix.execute(GraphixRequest("{ me }"), mapOf(FakeCall::class to call))
                result.data shouldBe mapOf("me" to "ada")
            }

            scenario("is not a GraphQL argument") {
                val graphix =
                    Graphix {
                        resolvers(FrameworkParameterQueries())
                        contextParameter(FakeCall::class)
                    }
                graphix.sdl() shouldContain "me: String!"
            }

            scenario("sits beside the DataFetchingEnvironment and an @Argument on one function") {
                val graphix =
                    Graphix {
                        resolvers(FrameworkParameterQueries())
                        contextParameter(FakeCall::class)
                    }
                val result =
                    graphix.execute(
                        GraphixRequest("""{ stamp(suffix: "x") }"""),
                        mapOf(FakeCall::class to call),
                    )
                result.data shouldBe mapOf("stamp" to "ada:stamp:x")
            }

            scenario("ahead of a @SchemaMapping parent, the parent is still the parent") {
                val graphix =
                    Graphix {
                        resolvers(FrameworkParameterQueries(), BadgeFields())
                        contextParameter(FakeCall::class)
                    }
                val result =
                    graphix.execute(
                        GraphixRequest("{ badge { id owner } }"),
                        mapOf(FakeCall::class to call),
                    )
                result.isOk shouldBe true
                result.data shouldBe mapOf("badge" to mapOf("id" to "t1", "owner" to "ada/t1"))
            }

            scenario("reaches a subscription too") {
                val graphix =
                    Graphix {
                        resolvers(GreetingQueries(), CallerSubscriptions())
                        contextParameter(FakeCall::class)
                    }
                val events =
                    graphix
                        .subscribe(GraphixRequest("subscription { callers }"), mapOf(FakeCall::class to call))
                        .toList()
                events.map { it.data } shouldBe listOf(mapOf("callers" to "ada"))
            }

            scenario("missing from the context, the operation fails naming the type") {
                val graphix =
                    Graphix {
                        resolvers(FrameworkParameterQueries())
                        contextParameter(FakeCall::class)
                    }
                val result = graphix.execute(GraphixRequest("{ me }"))
                result.isOk shouldBe false
                result.errors.single().message shouldContain FakeCall::class.qualifiedName!!
            }

            scenario("never registered, schema build refuses the parameter") {
                val failure = shouldThrow<GraphixException> { Graphix { resolvers(FrameworkParameterQueries()) } }
                failure.message shouldContain "must be @Argument"
            }
        }

        feature("graphql-java's GraphQLContext") {
            scenario("binds without registration") {
                val graphix = Graphix { resolvers(ContextBagQueries()) }
                val result = graphix.execute(GraphixRequest("{ who }"), mapOf(Caller::class to Caller("fr")))
                result.data shouldBe mapOf("who" to "fr")
            }
        }

        feature("@BatchMapping, which used to classify parameters by itself") {
            scenario("binds a registered type declared ahead of the parents") {
                val graphix =
                    Graphix {
                        resolvers(FrameworkParameterQueries())
                        resolvers(BadgeBatch())
                        contextParameter(FakeCall::class)
                    }
                val result =
                    graphix.execute(GraphixRequest("{ badge { stamp } }"), mapOf(FakeCall::class to call))

                result.errors shouldBe emptyList()
                (result.data?.get("badge") as Map<*, *>)["stamp"] shouldBe "ada/t1"
            }

            scenario("binds graphql-java's context bag too") {
                val graphix =
                    Graphix {
                        resolvers(FrameworkParameterQueries())
                        resolvers(BadgeContextBatch())
                        // Only so the `me` root in the same class builds; the batch needs nothing.
                        contextParameter(FakeCall::class)
                    }
                val result =
                    graphix.execute(GraphixRequest("{ badge { tag } }"), mapOf(Caller::class to Caller("fr")))

                result.errors shouldBe emptyList()
                (result.data?.get("badge") as Map<*, *>)["tag"] shouldBe "fr"
            }
        }

        feature("the SDL path validates like the annotation path") {
            scenario("a root parameter that is neither @Argument nor registered is refused at build") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            schemaLocations(emptyList())
                            resolvers(UnregisteredQueries())
                        }
                    }
                failure.message shouldContain "must be @Argument"
            }
        }
    })
