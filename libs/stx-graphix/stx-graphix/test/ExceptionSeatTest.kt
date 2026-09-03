package com.softistx.graphix

import com.softistx.graphix.execute.OperationScope
import com.softistx.graphix.fixture.BatchBoomQueries
import com.softistx.graphix.fixture.BlockingBoomQueries
import com.softistx.graphix.fixture.Boom
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.MidStreamBoomSubscriptions
import com.softistx.graphix.fixture.SubscribeBoomSubscriptions
import com.softistx.graphix.fixture.SuspendBoomQueries
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.SimpleDataFetcherExceptionHandler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * What actually reaches a `DataFetcherExceptionHandler`, measured rather than assumed.
 *
 * Every rule the exception-handler feature is built on is here: which throws reach the seat at all,
 * what wrapper each arrives in, and whether the operation's `CoroutineScope` is still alive when the
 * handler runs. Reading graphql-java's source answers the first two on paper — `ExecutionStrategy`
 * routes both a synchronous throw and a failed `CompletableFuture` through `handleFetchingException`
 * — but the wrapper depends on how *this* library invokes a resolver, which is `callBy` for a plain
 * function and `future { callSuspendBy(...) }` for a suspend one. Those are two different wrappers,
 * and unwrapping only one of them would leave half the resolvers undispatchable.
 */
class ExceptionSeatTest :
    FeatureSpec({
        feature("what reaches the seat") {
            scenario("a non-suspend resolver throw arrives wrapped by reflection") {
                val seen = Recorder()
                Graphix {
                    resolvers(BlockingBoomQueries())
                    engine { defaultDataFetcherExceptionHandler(seen) }
                }.execute(GraphixRequest("{ bang }"))

                // `callBy` is reflection, so the user's exception is the *cause*, not the exception.
                seen.exceptions.single()::class.qualifiedName shouldBe "java.lang.reflect.InvocationTargetException"
                seen.unwrapped().shouldContain(Boom::class)
            }

            scenario("a suspend resolver throw arrives through the future bridge") {
                val seen = Recorder()
                Graphix {
                    resolvers(SuspendBoomQueries())
                    engine { defaultDataFetcherExceptionHandler(seen) }
                }.execute(GraphixRequest("{ bang }"))

                seen.exceptions.size shouldBe 1
                seen.unwrapped().shouldContain(Boom::class)
            }

            scenario("a @BatchMapping throw reaches the same seat, through the DataLoader's future") {
                val seen = Recorder()
                Graphix {
                    resolvers(BatchBoomQueries())
                    engine { defaultDataFetcherExceptionHandler(seen) }
                }.execute(GraphixRequest("{ products { reviews { id } } }"))

                seen.unwrapped().shouldContain(Boom::class)
            }

            scenario("a subscription that throws before returning its flow reaches it too") {
                val seen = Recorder()
                Graphix {
                    resolvers(GreetingQueries(), SubscribeBoomSubscriptions())
                    engine { defaultDataFetcherExceptionHandler(seen) }
                }.subscribe(GraphixRequest("subscription { ticks }")).toList()

                seen.unwrapped().shouldContain(Boom::class)
            }
        }

        feature("what does not") {
            scenario("a subscription flow that throws mid-stream escapes subscribe entirely") {
                val seen = Recorder()
                val graphql =
                    Graphix {
                        resolvers(GreetingQueries(), MidStreamBoomSubscriptions())
                        engine { defaultDataFetcherExceptionHandler(seen) }
                    }

                // Not a GraphixResult carrying errors — the throw comes out of the flow at the
                // collector, which is why SSE leaks it while the graphql-ws session catches it.
                shouldThrow<Throwable> {
                    graphql.subscribe(GraphixRequest("subscription { ticks }")).toList()
                }
                seen.exceptions.isEmpty() shouldBe true
            }
        }

        feature("the operation scope") {
            scenario("is still alive while the handler runs, so a suspend handler can be bridged") {
                val seen = Recorder()
                Graphix {
                    resolvers(SuspendBoomQueries())
                    engine { defaultDataFetcherExceptionHandler(seen) }
                }.execute(GraphixRequest("{ bang }"))

                seen.scopeWasActive.single() shouldBe true
            }
        }
    })

/** Records what the seat was handed, without changing the outcome. */
private class Recorder : DataFetcherExceptionHandler {
    val exceptions = CopyOnWriteArrayList<Throwable>()
    val scopeWasActive = CopyOnWriteArrayList<Boolean>()

    override fun handleException(parameters: DataFetcherExceptionHandlerParameters): CompletableFuture<DataFetcherExceptionHandlerResult> {
        exceptions += parameters.exception
        // Read now, not later: `execute` cancels the job in its `finally`, so asking the scope
        // after the call has returned answers about the wrong moment.
        parameters.dataFetchingEnvironment.graphQlContext
            .get<CoroutineScope>(OperationScope)
            ?.let { scopeWasActive += it.isActive }
        // Delegate rather than answer: a handler returning *no* errors is not a neutral observer.
        // It empties `result.errors`, and `subscribeOnce` then reads the failed subscription as a
        // query and reports "use execute" instead of the throw. Measured, not guessed.
        return SimpleDataFetcherExceptionHandler().handleException(parameters)
    }

    /** The class of each recorded exception once every wrapper has been peeled off. */
    fun unwrapped(): List<KClass<*>> = exceptions.map { generateSequence(it) { failure -> failure.cause }.last()::class }
}
