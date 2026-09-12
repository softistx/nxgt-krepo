package com.softistx.graphix

import com.softistx.graphix.fixture.Caller
import com.softistx.graphix.fixture.ContextBagQueries
import com.softistx.graphix.fixture.CountSubscriptions
import com.softistx.graphix.fixture.FakeCall
import com.softistx.graphix.fixture.FrameworkParameterQueries
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.intercept.get
import com.softistx.graphix.intercept.intercept
import com.softistx.graphix.intercept.put
import com.softistx.graphix.schema.contextParameter
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

/**
 * What an interceptor may do to an operation, over both `execute` and `subscribe`.
 *
 * The `Flow` shape is the thing being pinned as much as the hooks are: one interceptor written once
 * has to read the same for a query, which is one result, and for a subscription, which is several.
 */
class InterceptorTest :
    FeatureSpec({

        feature("the context") {
            scenario("an interceptor puts a value a resolver then reads") {
                val graphix =
                    Graphix {
                        resolvers(ContextBagQueries())
                        intercept {
                            put(Caller("fr"))
                            proceed()
                        }
                    }
                graphix.execute(GraphixRequest("{ who }")).data shouldBe mapOf("who" to "fr")
            }

            scenario("it sees what the caller passed, and what an earlier interceptor put") {
                val seen = mutableListOf<String?>()
                val graphix =
                    Graphix {
                        resolvers(ContextBagQueries())
                        intercept {
                            seen += get<Caller>()?.locale
                            put(Caller("de"))
                            proceed()
                        }
                        intercept {
                            seen += get<Caller>()?.locale
                            proceed()
                        }
                    }
                graphix.execute(GraphixRequest("{ who }"), mapOf(Caller::class to Caller("en")))
                seen shouldBe listOf("en", "de")
            }

            scenario("it cannot unmake the operation by reusing a reserved key") {
                // OperationScope is what every data fetcher needs; the engine's keys go in last.
                val graphix =
                    Graphix {
                        resolvers(GreetingQueries())
                        intercept {
                            put(String::class, "not a scope")
                            proceed()
                        }
                    }
                graphix.execute(GraphixRequest("{ hello }")).data shouldBe mapOf("hello" to "world")
            }
        }

        feature("the request") {
            scenario("an interceptor rewrites the document before the engine sees it") {
                val graphix =
                    Graphix {
                        resolvers(GreetingQueries())
                        intercept {
                            request = request.copy(query = "{ hello }")
                            proceed()
                        }
                    }
                graphix.execute(GraphixRequest("{ nothingLikeThis }")).data shouldBe mapOf("hello" to "world")
            }
        }

        feature("the response") {
            scenario("an interceptor rewrites the single result of a query") {
                val graphix =
                    Graphix {
                        resolvers(GreetingQueries())
                        intercept {
                            proceed().map { it.copy(extensions = mapOf("seen" to true)) }
                        }
                    }
                graphix.execute(GraphixRequest("{ hello }")).extensions shouldBe mapOf("seen" to true)
            }

            scenario("the same line rewrites every event of a subscription") {
                val graphix =
                    Graphix {
                        resolvers(GreetingQueries(), CountSubscriptions())
                        intercept {
                            proceed().map { it.copy(extensions = mapOf("seen" to true)) }
                        }
                    }
                val events = graphix.subscribe(GraphixRequest("subscription { counts }")).toList()
                events.map { it.data } shouldBe listOf(mapOf("counts" to 1), mapOf("counts" to 2))
                events.map { it.extensions } shouldBe List(2) { mapOf("seen" to true) }
            }
        }

        feature("short-circuiting") {
            scenario("an interceptor that does not proceed answers on its own") {
                var reached = false
                val graphix =
                    Graphix {
                        resolvers(GreetingQueries())
                        intercept {
                            flowOf(GraphixResult(data = null, errors = listOf(GraphixError("unauthenticated"))))
                        }
                        intercept {
                            reached = true
                            proceed()
                        }
                    }
                val result = graphix.execute(GraphixRequest("{ hello }"))
                result.isOk shouldBe false
                result.errors.single().message shouldBe "unauthenticated"
                reached shouldBe false
            }

            scenario("it short-circuits a subscription the same way") {
                val graphix =
                    Graphix {
                        resolvers(GreetingQueries(), CountSubscriptions())
                        intercept { flowOf(GraphixResult(data = null, errors = listOf(GraphixError("no")))) }
                    }
                val events = graphix.subscribe(GraphixRequest("subscription { counts }")).toList()
                events
                    .single()
                    .errors
                    .single()
                    .message shouldBe "no"
            }
        }

        feature("ordering") {
            scenario("interceptors run outermost-first, in registration order") {
                val order = mutableListOf<String>()
                val graphix =
                    Graphix {
                        resolvers(GreetingQueries())
                        intercept {
                            order += "first in"
                            val result = proceed()
                            order += "first out"
                            result
                        }
                        intercept {
                            order += "second in"
                            val result = proceed()
                            order += "second out"
                            result
                        }
                    }
                graphix.execute(GraphixRequest("{ hello }"))
                order shouldBe listOf("first in", "second in", "second out", "first out")
            }
        }

        feature("a framework parameter") {
            scenario("an interceptor can supply what a resolver takes as one") {
                val graphix =
                    Graphix {
                        resolvers(FrameworkParameterQueries())
                        contextParameter(FakeCall::class)
                        intercept {
                            put(FakeCall(mapOf("X-User" to "grace")))
                            proceed()
                        }
                    }
                graphix.execute(GraphixRequest("{ me }")).data shouldBe mapOf("me" to "grace")
            }
        }
    })
