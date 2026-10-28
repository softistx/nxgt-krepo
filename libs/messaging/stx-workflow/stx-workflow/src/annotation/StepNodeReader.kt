package com.softistx.workflow.annotation

import com.softistx.workflow.dsl.RetryBuilder
import com.softistx.workflow.dsl.RetryPolicy
import com.softistx.workflow.dsl.exponential
import com.softistx.workflow.dsl.fixed
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.time.Duration.Companion.milliseconds

internal fun stepNode(
    function: KFunction<*>,
    step: Step,
    context: KType,
    undo: Map<String, KFunction<*>>,
    problems: MutableList<String>,
): AnnotatedNode {
    val name = step.name.ifEmpty { function.name }
    function.requireReturns(context, problems)
    if (function.valueParameters.isNotEmpty()) problems += "${function.name} is a @Step and must take no parameters"
    undo[name]?.requireScopeOn(context, problems)
    return StepNode(
        order = step.order,
        name = name,
        body = function,
        compensation = undo[name],
        retry = function.findAnnotation<Retry>()?.policy(),
        timeout = function.findAnnotation<Timeout>()?.millis?.milliseconds,
    )
}

private fun Retry.policy(): RetryPolicy =
    RetryBuilder()
        .apply {
            times = this@policy.times
            backoff =
                if (factor <= 1.0) {
                    fixed(delayMillis.milliseconds)
                } else {
                    exponential(delayMillis.milliseconds, factor, maxMillis.milliseconds)
                }
        }.build()
