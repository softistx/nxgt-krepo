package com.strange.graphix

import graphql.language.Definition
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.parser.Parser

/** Which GraphQL operation a document selected. */
enum class GraphixOperation {
    QUERY,
    MUTATION,
    SUBSCRIPTION,
}

/**
 * The operation [GraphixRequest.operationName] selects.
 *
 * `null` when the document does not parse, names a missing operation, or has several
 * operations and no name — [Graphix.execute] then reports those as GraphQL errors.
 */
fun GraphixRequest.operation(): GraphixOperation? {
    val document =
        try {
            Parser().parseDocument(query)
        } catch (_: Exception) {
            return null
        }
    val operations = document.definitionsOfType<OperationDefinition>()
    val selected =
        when {
            !operationName.isNullOrBlank() -> operations.find { it.name == operationName }
            operations.size == 1 -> operations.single()
            else -> null
        } ?: return null
    return when (selected.operation) {
        OperationDefinition.Operation.MUTATION -> GraphixOperation.MUTATION
        OperationDefinition.Operation.SUBSCRIPTION -> GraphixOperation.SUBSCRIPTION
        OperationDefinition.Operation.QUERY -> GraphixOperation.QUERY
    }
}

/** `true` when [operation] is a subscription. A document that does not parse is not. */
fun GraphixRequest.isSubscription(): Boolean = operation() == GraphixOperation.SUBSCRIPTION

private inline fun <reified T : Definition<*>> Document.definitionsOfType(): List<T> = getDefinitionsOfType(T::class.java)
