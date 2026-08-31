package com.strange.graphix.validation

import graphql.execution.ResultPath
import graphql.execution.instrumentation.fieldvalidation.FieldAndArguments
import graphql.execution.instrumentation.fieldvalidation.FieldValidation
import graphql.execution.instrumentation.fieldvalidation.SimpleFieldValidation
import graphql.validation.QueryComplexityLimits
import java.util.Optional

/**
 * Per-operation complexity limits, put in `execute`'s context map as
 * `GraphixLimits::class to GraphixLimits(...)`.
 *
 * graphql-java 26 reads [QueryComplexityLimits] off `GraphQLContext` during validation.
 * This is the Kotlin face of that: a value, not a global `setDefaultLimits`.
 */
class GraphixLimits(
    val maxDepth: Int,
    val maxFields: Int = QueryComplexityLimits.DEFAULT_MAX_FIELDS_COUNT,
) {
    init {
        require(maxDepth > 0) { "maxDepth must be positive" }
        require(maxFields > 0) { "maxFields must be positive" }
    }

    internal fun toJava(): QueryComplexityLimits =
        QueryComplexityLimits
            .newLimits()
            .maxDepth(maxDepth)
            .maxFieldsCount(maxFields)
            .build()
}

/** One argument-bearing field, for a [GraphixValidationBuilder.field] rule. */
class GraphixFieldCheck internal constructor(
    private val fieldAndArguments: FieldAndArguments,
) {
    /** The field's GraphQL name (the selection, not the Kotlin function). */
    val name: String get() = fieldAndArguments.field.name

    /** GraphQL argument [name], already coerced, or `null` if omitted. */
    fun argument(name: String): Any? = fieldAndArguments.getArgumentValue(name)
}

/**
 * graphql-java 26's validation, declared on the builder rather than wired as instrumentation
 * by hand.
 *
 * Complexity limits land in `GraphQLContext` next to the operation [kotlinx.coroutines.CoroutineScope]
 * — they are per operation, not a process-wide default. Field rules run before execution, on
 * already-coerced arguments; they must not suspend (graphql-java's hook is not a coroutine).
 */
class GraphixValidationBuilder internal constructor() {
    var maxDepth: Int? = null
        set(value) {
            if (value != null) require(value > 0) { "maxDepth must be positive" }
            field = value
        }

    var maxFields: Int? = null
        set(value) {
            if (value != null) require(value > 0) { "maxFields must be positive" }
            field = value
        }

    private var disabled = false
    private val fieldRules = mutableListOf<Pair<String, GraphixFieldCheck.() -> String?>>()

    /** Turns complexity checking off for this engine. graphql-java 26 otherwise defaults to 100 / 100_000. */
    fun none() {
        disabled = true
    }

    /**
     * A rule on the field at [path] (`"/shout"`, `/`-separated). Returning a string is the
     * complaint; `null` passes. Arguments are already coerced.
     */
    fun field(
        path: String,
        rule: GraphixFieldCheck.() -> String?,
    ) {
        fieldRules += path to rule
    }

    internal fun build(): GraphixValidation {
        val limits =
            when {
                disabled -> {
                    QueryComplexityLimits.NONE
                }

                maxDepth != null || maxFields != null -> {
                    QueryComplexityLimits
                        .newLimits()
                        .maxDepth(maxDepth ?: QueryComplexityLimits.DEFAULT_MAX_DEPTH)
                        .maxFieldsCount(maxFields ?: QueryComplexityLimits.DEFAULT_MAX_FIELDS_COUNT)
                        .build()
                }

                else -> {
                    null
                }
            }
        return GraphixValidation(limits, fieldRules.toList())
    }
}

internal class GraphixValidation(
    val complexityLimits: QueryComplexityLimits?,
    val fieldRules: List<Pair<String, GraphixFieldCheck.() -> String?>>,
) {
    fun fieldValidation(): FieldValidation? {
        if (fieldRules.isEmpty()) return null
        val simple = SimpleFieldValidation()
        fieldRules.forEach { (path, rule) ->
            simple.addRule(ResultPath.parse(path)) { fieldAndArguments, env ->
                val message = GraphixFieldCheck(fieldAndArguments).rule()
                if (message == null) {
                    Optional.empty()
                } else {
                    Optional.of(env.mkError(message, fieldAndArguments))
                }
            }
        }
        return simple
    }
}
