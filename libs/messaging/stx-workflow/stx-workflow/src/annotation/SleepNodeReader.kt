package com.softistx.workflow.annotation

import kotlin.reflect.KFunction
import kotlin.reflect.full.valueParameters
import kotlin.time.Duration

internal fun sleepNode(
    function: KFunction<*>,
    sleep: Sleep,
    problems: MutableList<String>,
): AnnotatedNode {
    if (function.isSuspend) problems += "${function.name} is a @Sleep and must not suspend; it computes a Duration"
    if (function.returnType.classifier != Duration::class) problems += "${function.name} is a @Sleep and must return a Duration"
    if (function.valueParameters.isNotEmpty()) problems += "${function.name} is a @Sleep and must take no parameters"
    function.refuse<Retry>("a @Sleep has nothing to retry", problems)
    function.refuse<Timeout>("a @Sleep is its own clock", problems)
    return SleepNode(sleep.order, sleep.name.ifEmpty { function.name }, function)
}
