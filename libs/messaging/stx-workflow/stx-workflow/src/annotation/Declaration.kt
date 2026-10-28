package com.softistx.workflow.annotation

import com.softistx.workflow.Workflow
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.jvm.isAccessible

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

        private fun duplicates(orders: List<Int>): List<Int> =
            orders
                .groupBy { it }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
    }
}
