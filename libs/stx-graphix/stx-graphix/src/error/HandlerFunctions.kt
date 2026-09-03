package com.softistx.graphix.error

import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixException
import com.softistx.graphix.schema.isArgument
import com.softistx.graphix.schema.isFrameworkParameter
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters

/** One `@ExceptionMapping` function, with the two parameters dispatch has to fill itself. */
internal data class HandlerFunction(
    val instance: Any,
    val function: KFunction<*>,
    /** The exception type this one answers for. */
    val exceptionType: KClass<out Throwable>,
    val exceptionParameter: KParameter,
    /** The `GraphixError` parameter, when the function asked for one to edit. */
    val errorParameter: KParameter?,
)

/**
 * Every `@ExceptionMapping` function on [instance], validated.
 *
 * Discovery is `memberFunctions`, not `declaredMemberFunctions`, so a handler inherited from a base
 * class counts — the same choice `mappingFunctions` makes for resolvers.
 */
internal fun Any.handlerFunctions(contextTypes: Set<KClass<*>>): List<HandlerFunction> =
    this::class
        .memberFunctions
        .filter { it.hasAnnotation<ExceptionMapping>() }
        .map { function -> handlerFunction(this, function, contextTypes) }

private fun handlerFunction(
    instance: Any,
    function: KFunction<*>,
    contextTypes: Set<KClass<*>>,
): HandlerFunction {
    val exceptionParameter =
        function.valueParameters.firstOrNull { it.throwableType() != null && !it.isFrameworkParameter(contextTypes) }
            ?: throw GraphixException(
                "@ExceptionMapping ${function.name} needs a parameter of the exception type it handles",
            )
    val errorParameter = function.valueParameters.firstOrNull { it != exceptionParameter && it.isGraphixError() }
    function.requireHandlerParameters(exceptionParameter, errorParameter, contextTypes)
    return HandlerFunction(
        instance = instance,
        function = function,
        exceptionType = exceptionParameter.throwableType()!!,
        exceptionParameter = exceptionParameter,
        errorParameter = errorParameter,
    )
}

/**
 * Refuses anything the framework cannot fill, at schema build rather than at the first throw.
 *
 * A handler runs only when something has already gone wrong, so a parameter this could not classify
 * would fail on the day the application is least able to absorb another failure.
 */
private fun KFunction<*>.requireHandlerParameters(
    exception: KParameter,
    error: KParameter?,
    contextTypes: Set<KClass<*>>,
) {
    valueParameters.forEach { parameter ->
        if (parameter == exception || parameter == error) return@forEach
        if (parameter.isFrameworkParameter(contextTypes)) return@forEach
        val what =
            if (parameter.isArgument()) {
                "@Argument, and a handler is not a field"
            } else {
                "neither the exception, a GraphixError to edit, nor a framework parameter"
            }
        throw GraphixException(
            "@ExceptionMapping $name parameter '${parameter.name}' is $what. " +
                "It may be the exception, a GraphixError, a DataFetchingEnvironment, a GraphQLContext, " +
                "or a type registered with contextParameter(...).",
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun KParameter.throwableType(): KClass<out Throwable>? =
    (type.classifier as? KClass<*>)?.takeIf { it.isSubclassOf(Throwable::class) } as KClass<out Throwable>?

private fun KParameter.isGraphixError(): Boolean = type.classifier == GraphixError::class
