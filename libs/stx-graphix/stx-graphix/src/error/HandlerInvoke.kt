package com.softistx.graphix.error

import com.softistx.graphix.GraphixError
import com.softistx.graphix.execute.invokeOn
import kotlin.reflect.KParameter
import kotlin.reflect.full.valueParameters

/**
 * A discovered `@ExceptionMapping` function as something dispatch can call.
 *
 * There is no JSON decoding and no `bindArguments` here, because a handler has no `@Argument`: every
 * parameter is the exception, the error, or a framework value. That is the whole binding.
 */
internal fun HandlerFunction.handling(): ErrorHandling =
    ErrorHandling { failure, error, context ->
        val bound = LinkedHashMap<KParameter, Any?>()
        function.valueParameters.forEach { parameter ->
            bound[parameter] =
                when (parameter) {
                    exceptionParameter -> failure
                    errorParameter -> error
                    else -> context.frameworkValue(parameter)
                }
        }
        function.invokeOn(instance, bound) as GraphixError?
    }
