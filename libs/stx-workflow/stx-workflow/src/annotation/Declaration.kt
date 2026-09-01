package com.softistx.workflow.annotation

import com.softistx.workflow.Workflow
import com.softistx.workflow.dsl.RetryBuilder
import com.softistx.workflow.dsl.RetryPolicy
import com.softistx.workflow.dsl.Signal
import com.softistx.workflow.dsl.StepScope
import com.softistx.workflow.dsl.exponential
import com.softistx.workflow.dsl.fixed
import com.softistx.workflow.dsl.signal
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * An annotated class, read and checked, as an ordered list of nodes.
 *
 * Every problem it can find, it finds **here** — when the workflow is built, at startup, next to
 * the mistake — rather than on the resume six weeks later that first reaches the broken node. That
 * is the same bargain the DSL's duplicate-name check makes, and it is most of what this file is.
 */
internal class Declaration(
    val name: String,
    val nodes: List<AnnotatedNode>,
) {
    companion object {
        fun of(
            type: KClass<*>,
            context: KType,
            children: List<Workflow<*>>,
        ): Declaration {
            val meta =
                type.findAnnotation<WorkflowDefinition>()
                    ?: throw IllegalArgumentException("${type.qualifiedName} is not annotated @WorkflowDefinition")

            val functions = type.memberExtensionFunctions.toList().onEach { it.isAccessible = true }
            val problems = mutableListOf<String>()
            val undo = compensations(functions, problems)
            val results = childResults(functions, problems)
            val nodes = nodes(functions, context, undo, results, children.associateBy { it.name }, problems)

            if (nodes.isEmpty()) problems += "it declares no @Step, @Await, @Sleep or @Child"
            undo.keys
                .filterNot { step -> nodes.any { it is StepNode && it.name == step } }
                .forEach { problems += "@Compensate(\"$it\") names no @Step" }
            results.keys
                .filterNot { node -> nodes.any { it is ChildNode && it.name == node } }
                .forEach { problems += "@ChildResult(\"$it\") names no @Child" }
            duplicates(nodes.map { it.order }).forEach { problems += "two nodes claim order $it" }

            require(problems.isEmpty()) {
                "${type.qualifiedName} is not a usable workflow:\n" + problems.joinToString("\n") { "  - $it" }
            }
            return Declaration(meta.name, nodes.sortedBy { it.order })
        }

        /** Every `@ChildResult`, by the child node it follows. A node's name, not a function's. */
        private fun childResults(
            functions: List<KFunction<*>>,
            problems: MutableList<String>,
        ): Map<String, KFunction<*>> {
            val found = mutableMapOf<String, KFunction<*>>()
            for (function in functions) {
                val node = function.findAnnotation<ChildResult>()?.child ?: continue
                if (found.put(node, function) != null) problems += "child '$node' has two @ChildResult functions"
            }
            return found
        }

        /** Every `@Compensate`, by the step it undoes. A step's name, not a function's. */
        private fun compensations(
            functions: List<KFunction<*>>,
            problems: MutableList<String>,
        ): Map<String, KFunction<*>> {
            val found = mutableMapOf<String, KFunction<*>>()
            for (function in functions) {
                val step = function.findAnnotation<Compensate>()?.step ?: continue
                if (found.put(step, function) != null) problems += "step '$step' has two compensations"
            }
            return found
        }

        private fun nodes(
            functions: List<KFunction<*>>,
            context: KType,
            undo: Map<String, KFunction<*>>,
            results: Map<String, KFunction<*>>,
            children: Map<String, Workflow<*>>,
            problems: MutableList<String>,
        ): List<AnnotatedNode> =
            functions.mapNotNull { function ->
                val kinds =
                    listOfNotNull(
                        function.findAnnotation<Step>(),
                        function.findAnnotation<Await>(),
                        function.findAnnotation<Sleep>(),
                        function.findAnnotation<Child>(),
                    )
                when {
                    kinds.isEmpty() -> {
                        null
                    }

                    kinds.size > 1 -> {
                        problems += "${function.name} is annotated as more than one kind of node"
                        null
                    }

                    else -> {
                        node(function, kinds.single(), context, undo, results, children, problems)
                    }
                }
            }

        private fun node(
            function: KFunction<*>,
            kind: Annotation,
            context: KType,
            undo: Map<String, KFunction<*>>,
            results: Map<String, KFunction<*>>,
            children: Map<String, Workflow<*>>,
            problems: MutableList<String>,
        ): AnnotatedNode? {
            val before = problems.size
            function.requireScopeOn(context, problems)
            val node =
                when (kind) {
                    is Step -> stepNode(function, kind, context, undo, problems)
                    is Await -> awaitNode(function, kind, context, problems)
                    is Sleep -> sleepNode(function, kind, problems)
                    is Child -> childNode(function, kind, context, results, children, problems)
                    else -> null
                }
            return node.takeIf { problems.size == before }
        }

        /**
         * Reads a `@Child` and the `@ChildResult` that follows it.
         *
         * Everything checkable is checked here: that the named workflow was handed to `workflowOf`,
         * that the starting context this function computes is the type that workflow reads, and
         * that the result function is handed the same type back. All three are the mistakes that
         * would otherwise surface as a decode failure on the first instance to reach the node.
         */
        private fun childNode(
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

        private fun stepNode(
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

        private fun awaitNode(
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

        private fun sleepNode(
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

        private fun duplicates(orders: List<Int>): List<Int> =
            orders
                .groupBy { it }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
    }
}

/**
 * The receiver has to be `StepScope<C>` for this workflow's own `C`.
 *
 * Getting it wrong is the mistake this front end makes easy — the class names its context once per
 * function instead of once per class — so the message says which context was expected and where it
 * came from, rather than reporting a cast that failed somewhere in the engine an hour later.
 */
private fun KFunction<*>.requireScopeOn(
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

private fun KFunction<*>.requireReturns(
    context: KType,
    problems: MutableList<String>,
) {
    if (returnType != context) problems += "$name returns $returnType; a node returns the next context, $context"
}

private inline fun <reified A : Annotation> KFunction<*>.refuse(
    why: String,
    problems: MutableList<String>,
) {
    if (findAnnotation<A>() != null) problems += "$name carries @${A::class.simpleName}, and $why"
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
