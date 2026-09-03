package com.softistx.graphix.execute

import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter

/**
 * The `callBy` map for a member function: the receiver under its own key, then the bound values.
 *
 * kotlin-reflect has no `call(instance, args)` for a `callBy` — the instance is a parameter like any
 * other and goes in the same map. Every call site in this module needs that line, and one of them
 * having drifted from another is what `BatchLoaders.invokeBatchMapping` still carries a comment
 * about.
 */
internal fun KFunction<*>.argumentsWith(
    instance: Any,
    bound: Map<KParameter, Any?>,
): Map<KParameter, Any?> {
    val arguments = LinkedHashMap<KParameter, Any?>()
    val instanceParameter = instanceParameter ?: error("$name is not a member function")
    arguments[instanceParameter] = instance
    arguments.putAll(bound)
    return arguments
}
