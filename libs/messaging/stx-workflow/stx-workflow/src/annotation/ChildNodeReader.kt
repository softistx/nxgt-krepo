package com.softistx.workflow.annotation

import com.softistx.workflow.Workflow
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.serializer

/**
 * Reads a `@Child` and the `@ChildResult` that follows it.
 *
 * Everything checkable is checked here: that the named workflow was handed to `workflowOf`,
 * that the starting context this function computes is the type that workflow reads, and
 * that the result function is handed the same type back. All three are the mistakes that
 * would otherwise surface as a decode failure on the first instance to reach the node.
 */
internal fun childNode(
    function: KFunction<*>,
    child: Child,
    context: KType,
    results: Map<String, KFunction<*>>,
    children: Map<String, Workflow<*>>,
    problems: MutableList<String>,
): AnnotatedNode? {
    val name = child.name.ifEmpty { function.name }
    if (function.isSuspend) problems += "${function.name} is a @Child and must not suspend; it computes the child's context"
    if (function.valueParameters.isNotEmpty()) problems += "${function.name} is a @Child and must take no parameters"
    function.refuse<Retry>("a @Child retries nothing; the child has its own policies", problems)
    function.refuse<Timeout>("a @Child waits without a clock unless withinMillis says otherwise", problems)

    val declaration = children[child.workflow]
    if (declaration == null) {
        problems +=
            "${function.name} is a @Child of '${child.workflow}', which was not passed to workflowOf" +
            children.keys
                .sorted()
                .joinToString(prefix = " (given: ", postfix = ")")
                .takeIf { children.isNotEmpty() }
                .orEmpty()
        return null
    }
    requireSerializesAs(
        function.returnType,
        declaration,
        "${function.name} returns",
        "the context of '${child.workflow}'",
        problems,
    )

    val result = results[name]
    if (result == null) {
        problems += "@Child '$name' has no @ChildResult(\"$name\") to fold its result into the context"
        return null
    }
    result.requireScopeOn(context, problems)
    result.requireReturns(context, problems)
    val payload = result.valueParameters.singleOrNull()
    if (payload == null) {
        problems += "${result.name} is a @ChildResult and must take exactly one parameter, the child's final context"
        return null
    }
    requireSerializesAs(payload.type, declaration, "${result.name}'s parameter", "the context of '${child.workflow}'", problems)

    @Suppress("UNCHECKED_CAST")
    return ChildNode(
        order = child.order,
        name = name,
        child = declaration as Workflow<Any?>,
        start = function,
        body = result,
        deadline = child.withinMillis.takeIf { it > 0 }?.milliseconds,
    )
}

/**
 * A type has to be the one the child workflow reads and writes.
 *
 * Compared by serial name rather than by `KType`, because that is what actually has to match: the
 * child's context is stored encoded and read back with the child's own serializer, so two types that
 * serialize identically are interchangeable here and two that do not are not — whatever their
 * declarations look like. It is also the only handle a `Workflow<D>` offers on its `D` at runtime.
 */
private fun requireSerializesAs(
    type: KType,
    child: Workflow<*>,
    what: String,
    expected: String,
    problems: MutableList<String>,
) {
    val actual = runCatching { serializer(type).descriptor.serialName }.getOrNull()
    val wanted = child.serializer.descriptor.serialName
    when (actual) {
        null -> problems += "$what $type, which is not serializable, and $expected has to be"
        wanted -> Unit
        else -> problems += "$what $type, but $expected is $wanted"
    }
}
