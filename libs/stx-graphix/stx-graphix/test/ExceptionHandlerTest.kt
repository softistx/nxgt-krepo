package com.softistx.graphix

import com.softistx.graphix.error.GraphixErrorScope
import com.softistx.graphix.error.GraphixErrorType.BAD_REQUEST
import com.softistx.graphix.error.GraphixErrorType.INTERNAL_ERROR
import com.softistx.graphix.error.GraphixErrorType.NOT_FOUND
import com.softistx.graphix.error.GraphixExceptionHandler
import com.softistx.graphix.error.errors
import com.softistx.graphix.error.exceptionHandler
import com.softistx.graphix.error.get
import com.softistx.graphix.error.on
import com.softistx.graphix.error.withErrorType
import com.softistx.graphix.error.withExtension
import com.softistx.graphix.error.withMessage
import com.softistx.graphix.fixture.BlockingBoomQueries
import com.softistx.graphix.fixture.Boom
import com.softistx.graphix.fixture.Caller
import com.softistx.graphix.fixture.SmallBoom
import com.softistx.graphix.fixture.SmallBoomQueries
import com.softistx.graphix.fixture.SuspendBoomQueries
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.SimpleDataFetcherExceptionHandler
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * Turning an exception into the error a client should see.
 *
 * `ExceptionSeatTest` measured *what arrives*; this is what the application gets to say about it.
 */
class ExceptionHandlerTest :
    FeatureSpec({
        feature("the errors { } block") {
            scenario("a handler replaces the message and the classification") {
                val graphql =
                    Graphix {
                        resolvers(BlockingBoomQueries())
                        errors {
                            on<Boom> { failure -> error.withMessage("caught ${failure.message}").withErrorType(NOT_FOUND) }
                        }
                    }
                val error = graphql.execute(GraphixRequest("{ bang }")).errors.single()

                error.message shouldBe "caught boom"
                error.errorType shouldBe "NOT_FOUND"
            }

            scenario("what it does not edit is what would have been sent anyway") {
                val graphql =
                    Graphix {
                        resolvers(BlockingBoomQueries())
                        errors { on<Boom> { error.withErrorType(BAD_REQUEST) } }
                    }
                val error = graphql.execute(GraphixRequest("{ bang }")).errors.single()

                // The starting error is the one today's default would produce, so a handler that only
                // classifies does not have to restate the message, the path or the position.
                error.message shouldBe "boom"
                error.path shouldBe listOf("bang")
                error.locations.single().line shouldBe 1
                error.locations.single().column shouldBe 3
            }

            scenario("a suspend handler runs on the operation's scope") {
                val graphql =
                    Graphix {
                        resolvers(SuspendBoomQueries())
                        errors {
                            on<Boom> {
                                kotlinx.coroutines.yield()
                                error.withMessage("suspended")
                            }
                        }
                    }
                graphql
                    .execute(GraphixRequest("{ bang }"))
                    .errors
                    .single()
                    .message shouldBe "suspended"
            }

            scenario("registration order decides, so a broad handler registered first claims the subclass too") {
                val graphql =
                    Graphix {
                        resolvers(SmallBoomQueries())
                        errors {
                            on<Boom> { error.withMessage("general") }
                            on<SmallBoom> { error.withMessage("specific") }
                        }
                    }
                // The rule interceptors already follow: each is asked in turn, the first to answer
                // wins. Pinned so nobody reads "most specific" into it — a `SmallBoom` is a `Boom`,
                // and the `Boom` handler was asked first.
                graphql
                    .execute(GraphixRequest("{ bang }"))
                    .errors
                    .single()
                    .message shouldBe "general"
            }

            scenario("so narrowing means registering the specific one first") {
                val graphql =
                    Graphix {
                        resolvers(SmallBoomQueries())
                        errors {
                            on<SmallBoom> { error.withMessage("specific") }
                            on<Boom> { error.withMessage("general") }
                        }
                    }
                graphql
                    .execute(GraphixRequest("{ bang }"))
                    .errors
                    .single()
                    .message shouldBe "specific"
            }

            scenario("returning null means not mine, and the next handler is asked") {
                val graphql =
                    Graphix {
                        resolvers(SmallBoomQueries())
                        errors {
                            on<SmallBoom> { null }
                            on<Boom> { error.withMessage("general") }
                        }
                    }
                graphql
                    .execute(GraphixRequest("{ bang }"))
                    .errors
                    .single()
                    .message shouldBe "general"
            }

            scenario("an unclaimed exception keeps the answer it would have had") {
                val graphql =
                    Graphix {
                        resolvers(BlockingBoomQueries())
                        errors { on<IllegalStateException> { error.withMessage("wrong one") } }
                    }
                val error = graphql.execute(GraphixRequest("{ bang }")).errors.single()

                error.message shouldBe "boom"
                error.errorType shouldBe "DataFetchingException"
            }

            scenario("fallback answers for what nothing else claimed") {
                val graphql =
                    Graphix {
                        resolvers(BlockingBoomQueries())
                        errors { fallback { error.withMessage("Internal error").withErrorType(INTERNAL_ERROR) } }
                    }
                val error = graphql.execute(GraphixRequest("{ bang }")).errors.single()

                error.message shouldBe "Internal error"
                error.errorType shouldBe "INTERNAL_ERROR"
            }
        }

        feature("against engine { }") {
            scenario("an explicit defaultDataFetcherExceptionHandler still wins") {
                val graphql =
                    Graphix {
                        resolvers(BlockingBoomQueries())
                        errors { on<Boom> { error.withMessage("handled") } }
                        // Engine customizers run last on purpose: writing this is an explicit choice
                        // and should beat the seat the errors { } block installs.
                        engine { defaultDataFetcherExceptionHandler(SimpleDataFetcherExceptionHandler()) }
                    }
                graphql
                    .execute(GraphixRequest("{ bang }"))
                    .errors
                    .single()
                    .message shouldBe "boom"
            }

            scenario("setting an execution strategy silently defeats it — the one trap worth naming") {
                val graphql =
                    Graphix {
                        resolvers(BlockingBoomQueries())
                        errors { on<Boom> { error.withMessage("handled") } }
                        // graphql-java applies defaultDataFetcherExceptionHandler only to strategies
                        // left null at build (GraphQL.Builder.build), so a strategy built here carries
                        // its own. Nothing warns. Pinned so the day it changes, this spec says so.
                        engine { queryExecutionStrategy(AsyncExecutionStrategy()) }
                    }
                graphql
                    .execute(GraphixRequest("{ bang }"))
                    .errors
                    .single()
                    .message shouldBe "boom"
            }
        }

        feature("a GraphixExceptionHandler") {
            scenario("one interface, one function, branching on the exception itself") {
                val graphql =
                    Graphix {
                        resolvers(SmallBoomQueries())
                        exceptionHandler(CatalogErrors())
                    }
                graphql
                    .execute(GraphixRequest("{ bang }"))
                    .errors
                    .single()
                    .message shouldBe "small: small boom"
            }

            scenario("it declines by returning null, and the next handler is asked") {
                val graphql =
                    Graphix {
                        resolvers(BlockingBoomQueries())
                        exceptionHandler(CatalogErrors())
                        errors { on<Boom> { error.withMessage("second") } }
                    }
                // CatalogErrors only claims SmallBoom, and a plain Boom is not one.
                graphql
                    .execute(GraphixRequest("{ bang }"))
                    .errors
                    .single()
                    .message shouldBe "second"
            }

            scenario("it reads the operation context and the failing field") {
                val graphql =
                    Graphix {
                        resolvers(BlockingBoomQueries())
                        exceptionHandler(CallerErrors())
                    }
                val result = graphql.execute(GraphixRequest("{ bang }"), mapOf(Caller::class to Caller("fr")))
                val error = result.errors.single()

                error.message shouldBe "boom for fr"
                error.extensions["field"] shouldBe "bang"
            }
        }
    })

class CatalogErrors : GraphixExceptionHandler {
    override suspend fun GraphixErrorScope.handle(failure: Throwable): GraphixError? =
        if (failure is SmallBoom) error.withMessage("small: ${failure.message}") else null
}

class CallerErrors : GraphixExceptionHandler {
    override suspend fun GraphixErrorScope.handle(failure: Throwable): GraphixError =
        error
            .withMessage("${failure.message} for ${get<Caller>()?.locale}")
            .withExtension("field", environment?.field?.name ?: "none")
}
