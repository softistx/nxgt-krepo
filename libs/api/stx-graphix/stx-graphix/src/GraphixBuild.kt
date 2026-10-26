package com.softistx.graphix

import com.softistx.graphix.error.ErrorHandlers
import com.softistx.graphix.execute.errorDispatch
import com.softistx.graphix.schema.graphQLSchema
import com.softistx.graphix.schema.loadSchemaFiles
import graphql.GraphQL
import graphql.execution.instrumentation.fieldvalidation.FieldValidationInstrumentation

/** Builds the engine from what [GraphixBuilder] accumulated. */
internal fun GraphixBuilder.assemble(): Graphix {
    val files = resourceLocations.loadSchemaFiles(resourceExtensions)
    val (schema, loaders) =
        graphQLSchema(
            instances,
            json,
            files,
            customScalars,
            kotlinScalars,
            fieldDirectives,
            typeResolvers,
            builtInScalars,
            contextTypes,
        )
    val builder = GraphQL.newGraphQL(schema)
    validation?.fieldValidation()?.let { builder.instrumentation(FieldValidationInstrumentation(it)) }
    val handlers = ErrorHandlers(errorHandlers.toList(), errorFallback)
    // Before the customizers, not after: `engine { defaultDataFetcherExceptionHandler(…) }` is an
    // explicit choice and should still win. What it cannot survive is
    // `engine { queryExecutionStrategy(…) }` — graphql-java only applies the default handler to
    // strategies left null at build, so setting one silently drops this seat. Documented, not
    // guessable from the API.
    if (!handlers.isEmpty()) builder.defaultDataFetcherExceptionHandler(errorDispatch(handlers, messages))
    engineCustomizers.forEach { with(it) { builder.customize() } }
    return Graphix(
        builder.build(),
        loaders,
        validation,
        introspection,
        messages,
        interceptors.toList(),
        handlers,
        maxListElements,
    )
}
