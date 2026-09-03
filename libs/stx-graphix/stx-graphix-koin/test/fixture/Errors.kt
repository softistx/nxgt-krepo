package com.softistx.graphix.koin.fixture

import com.softistx.graphix.GraphQLEngineCustomizer
import com.softistx.graphix.GraphixError
import com.softistx.graphix.error.GraphixErrorScope
import com.softistx.graphix.error.GraphixErrorType.NOT_FOUND
import com.softistx.graphix.error.GraphixExceptionHandler
import com.softistx.graphix.error.withErrorType
import com.softistx.graphix.error.withMessage
import com.softistx.graphix.koin.GraphixResolver
import com.softistx.graphix.schema.QueryMapping
import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.language.SourceLocation
import java.util.concurrent.CompletableFuture

/** Thrown by [BoomQueries], claimed by [KoinErrors]. */
class Boom : RuntimeException("boom")

class BoomQueries : GraphixResolver {
    @QueryMapping
    fun bang(): String = throw Boom()
}

/** A Koin single implementing the interface, so `fromKoin()` has something to collect. */
class KoinErrors : GraphixExceptionHandler {
    override suspend fun GraphixErrorScope.handle(failure: Throwable): GraphixError? =
        if (failure is Boom) error.withMessage("from koin: ${failure.message}").withErrorType(NOT_FOUND) else null
}

/**
 * A `GraphQLEngineCustomizer` single, reaching graphql-java itself rather than the Graphix builder.
 *
 * It is the one collectable whose effect never shows in the SDL, so a `getAll` regression that
 * dropped it would leave every other spec here green.
 */
fun taggedEngine() = GraphQLEngineCustomizer { defaultDataFetcherExceptionHandler(TaggedHandler) }

private object TaggedHandler : DataFetcherExceptionHandler {
    override fun handleException(parameters: DataFetcherExceptionHandlerParameters): CompletableFuture<DataFetcherExceptionHandlerResult> =
        CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().error(TaggedError).build())
}

/**
 * Written out rather than built with `GraphqlErrorBuilder`: it is F-bounded
 * (`B extends GraphqlErrorBuilder<B>`), and inferring that type argument from Kotlin puts the
 * compiler into a `StackOverflowError` with no file pointer.
 */
private object TaggedError : GraphQLError {
    override fun getMessage(): String = "handled by the engine customizer single"

    override fun getLocations(): List<SourceLocation> = emptyList()

    override fun getErrorType(): ErrorClassification = ErrorType.DataFetchingException
}
