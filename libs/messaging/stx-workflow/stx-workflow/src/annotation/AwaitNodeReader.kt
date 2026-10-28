package com.softistx.workflow.annotation

import com.softistx.workflow.dsl.Signal
import com.softistx.workflow.dsl.signal
import kotlinx.serialization.serializer
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.valueParameters
import kotlin.time.Duration.Companion.milliseconds

internal fun awaitNode(
    function: KFunction<*>,
    await: Await,
    context: KType,
    problems: MutableList<String>,
): AnnotatedNode? {
    function.requireReturns(context, problems)
    function.refuse<Retry>("an @Await has nothing to retry", problems)
    function.refuse<Timeout>("an @Await waits without a clock unless withinMillis says otherwise", problems)
    val payload = function.valueParameters.singleOrNull()
    if (payload == null) {
        problems += "${function.name} is an @Await and must take exactly one parameter, the signal's payload"
        return null
    }
    @Suppress("UNCHECKED_CAST")
    val key = signal(await.signal, serializer(payload.type)) as Signal<Any?>
    return AwaitNode(
        order = await.order,
        name = await.name.ifEmpty { function.name },
        signal = key,
        body = function,
        deadline = await.withinMillis.takeIf { it > 0 }?.milliseconds,
    )
}
