package com.softistx.graphix

import com.softistx.graphix.error.GraphixErrorType.INTERNAL_ERROR
import com.softistx.graphix.error.GraphixErrorType.UNAUTHORIZED
import com.softistx.graphix.error.errors
import com.softistx.graphix.error.get
import com.softistx.graphix.error.on
import com.softistx.graphix.error.withErrorType
import com.softistx.graphix.error.withMessage
import com.softistx.graphix.fixture.Boom
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.MidStreamBoomSubscriptions
import com.softistx.graphix.fixture.TickSubscriptions
import com.softistx.graphix.intercept.intercept
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList

/**
 * The two throws that never reach graphql-java, and so never reached a handler.
 *
 * An interceptor runs before the engine; a subscription's `Flow` fails after it. Both used to leave
 * the library as raw exceptions — a 500 rather than an `errors[]` — and `ExceptionSeatTest` pins
 * that as the starting point. Nothing here changes until a handler claims the throw.
 */
class OutsideFieldErrorTest :
    FeatureSpec({
        feature("an interceptor that throws") {
            scenario("is a raw throw when no handler claims it") {
                val graphql =
                    Graphix {
                        resolvers(GreetingQueries())
                        intercept { throw Boom() }
                    }
                shouldThrow<Boom> { graphql.execute(GraphixRequest("{ hello }")) }
            }

            scenario("becomes a GraphQL error once one does") {
                val graphql =
                    Graphix {
                        resolvers(GreetingQueries())
                        intercept { throw Boom("no token") }
                        errors { on<Boom> { failure -> error.withMessage(failure.message!!).withErrorType(UNAUTHORIZED) } }
                    }
                val result = graphql.execute(GraphixRequest("{ hello }"))

                result.data shouldBe null
                result.errors.single().message shouldBe "no token"
                result.errors.single().errorType shouldBe "UNAUTHORIZED"
            }

            scenario("reads the operation context, since there is no field to read") {
                val graphql =
                    Graphix {
                        resolvers(GreetingQueries())
                        intercept { throw Boom() }
                        errors { fallback { error.withMessage("as ${get<Caller>()?.name}") } }
                    }
                val result = graphql.execute(GraphixRequest("{ hello }"), mapOf(Caller::class to Caller("ada")))

                result.errors.single().message shouldBe "as ada"
            }

            scenario("a GraphixException still throws — that one is the caller's bug, not a client's error") {
                val graphql =
                    Graphix {
                        resolvers(GreetingQueries(), TickSubscriptions())
                        errors { fallback { error.withMessage("swallowed") } }
                    }
                // A subscription sent to execute. A fallback must not turn this into an errors[]
                // and hide it from the only person who can fix it.
                shouldThrow<GraphixException> { graphql.execute(GraphixRequest("subscription { ticks }")) }
            }
        }

        feature("a subscription flow that throws mid-stream") {
            scenario("still escapes when no handler claims it") {
                val graphql = Graphix { resolvers(GreetingQueries(), MidStreamBoomSubscriptions()) }

                shouldThrow<Boom> {
                    graphql.subscribe(GraphixRequest("subscription { ticks }")).toList()
                }
            }

            scenario("becomes a final event carrying the error once one does") {
                val graphql =
                    Graphix {
                        resolvers(GreetingQueries(), MidStreamBoomSubscriptions())
                        errors { on<Boom> { error.withMessage("stream failed").withErrorType(INTERNAL_ERROR) } }
                    }
                val events = graphql.subscribe(GraphixRequest("subscription { ticks }")).toList()

                // The event before the throw is kept: a stream that failed after emitting is not a
                // stream that never ran.
                events.first().data shouldBe mapOf("ticks" to 1)
                events
                    .last()
                    .errors
                    .single()
                    .message shouldBe "stream failed"
                events
                    .last()
                    .errors
                    .single()
                    .errorType shouldBe "INTERNAL_ERROR"
            }
        }
    })

private data class Caller(
    val name: String,
)
