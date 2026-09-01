package com.strange.workflow.annotation

import com.strange.workflow.Workflow
import com.strange.workflow.dsl.NodeSink
import com.strange.workflow.workflow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Turns an annotated class into a workflow.
 *
 * ```kotlin
 * val checkout = workflowOf<Checkout>(CheckoutWorkflow(stock, payments))
 * val engine = WorkflowEngine(RedisWorkflowStore(redis)) { register(checkout) }
 * ```
 *
 * What comes back is an ordinary `Workflow<C>` — the same object `workflow<C>("checkout") { }`
 * produces, built by the same builder through the same public verbs. There is no second engine and
 * no second set of rules: an annotated workflow and a written one are indistinguishable to
 * everything downstream, which is why the two front ends can coexist in one application and why a
 * workflow can be migrated from one to the other without touching a stored instance.
 *
 * `C` is given at the call site and **checked against the declaration**: every annotated function
 * has to be a member extension on `StepScope<C>` for this exact `C`. The class states its context
 * once per function, which is the one thing this front end makes easy to get wrong, so it is
 * checked here, at startup, with a message that names both types.
 *
 * Everything else a class can get wrong is checked at the same moment and reported **together** —
 * a compensation naming a step that does not exist, two nodes claiming one order, an `@Await` with
 * no payload parameter. Fixing three mistakes should take one run.
 *
 * ## What annotations cannot say
 *
 * There is no `@Branch` and no `@Parallel`. A branch's condition is a predicate and a fan-out's
 * merge is a function of several typed results; both are ordinary Kotlin in the DSL and would be
 * strings or magic method names here, checked at startup at best. A workflow that needs either is
 * written with `workflow { }` — which is the whole language, and is what this produces anyway.
 */
inline fun <reified C> workflowOf(definition: Any): Workflow<C> = workflowOf(definition, typeOf<C>())

@PublishedApi
internal fun <C> workflowOf(
    definition: Any,
    context: KType,
): Workflow<C> {
    val declaration = Declaration.of(definition::class, context)

    @Suppress("UNCHECKED_CAST")
    val serializer = serializer(context) as KSerializer<C>
    return workflow(declaration.name, serializer) {
        // The reader does not know `C`, and the sink is invariant in it. One cast, in one place,
        // over a builder that only ever receives values the reader has already type-checked.
        @Suppress("UNCHECKED_CAST")
        val sink = this as NodeSink<Any?>
        declaration.nodes.forEach { it.declare(sink, definition) }
    }
}
