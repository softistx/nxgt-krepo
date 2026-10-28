package com.softistx.workflow.annotation

import com.softistx.workflow.dsl.StepScope
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.findAnnotation

/**
 * The receiver has to be `StepScope<C>` for this workflow's own `C`.
 *
 * Getting it wrong is the mistake this front end makes easy — the class names its context once per
 * function instead of once per class — so the message says which context was expected and where it
 * came from, rather than reporting a cast that failed somewhere in the engine an hour later.
 */
internal fun KFunction<*>.requireScopeOn(
    context: KType,
    problems: MutableList<String>,
) {
    val receiver = extensionReceiverParameter?.type
    val declared =
        receiver
            ?.takeIf { it.classifier == StepScope::class }
            ?.arguments
            ?.singleOrNull()
            ?.type
    when {
        receiver == null -> problems += "$name must be a member extension on StepScope<$context>"
        declared == null -> problems += "$name is an extension on $receiver, not on StepScope<$context>"
        declared != context -> problems += "$name is an extension on StepScope<$declared>, but this workflow's context is $context"
    }
}

internal fun KFunction<*>.requireReturns(
    context: KType,
    problems: MutableList<String>,
) {
    if (returnType != context) problems += "$name returns $returnType; a node returns the next context, $context"
}

internal inline fun <reified A : Annotation> KFunction<*>.refuse(
    why: String,
    problems: MutableList<String>,
) {
    if (findAnnotation<A>() != null) problems += "$name carries @${A::class.simpleName}, and $why"
}
