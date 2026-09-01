package com.strange.workflow.annotation

import com.strange.workflow.Workflow
import com.strange.workflow.dsl.NodeSink
import com.strange.workflow.dsl.RetryPolicy
import com.strange.workflow.dsl.Signal
import com.strange.workflow.dsl.await
import com.strange.workflow.dsl.child
import com.strange.workflow.dsl.compensate
import com.strange.workflow.dsl.retry
import com.strange.workflow.dsl.sleep
import com.strange.workflow.dsl.step
import com.strange.workflow.dsl.timeout
import com.strange.workflow.dsl.within
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.time.Duration

/**
 * One node read off an annotated class, ready to be declared.
 *
 * These exist so that reading a class and building a workflow are two passes rather than one. The
 * first can reject a class with a message that names every problem at once — a compensation for a
 * step that does not exist, two nodes claiming the same order — while a single pass would fail on
 * whichever it met first and make fixing three mistakes take three runs.
 *
 * They are declared against `NodeSink<Any?>`: the reader does not know `C` statically, and the
 * declaration goes through exactly the same public verbs a hand-written workflow uses. That is the
 * point of the annotations — they are a front end onto the DSL, not a second engine.
 */
internal sealed class AnnotatedNode {
    abstract val order: Int
    abstract val name: String

    abstract fun declare(
        sink: NodeSink<Any?>,
        instance: Any,
    )
}

internal class StepNode(
    override val order: Int,
    override val name: String,
    private val body: KFunction<*>,
    private val compensation: KFunction<*>?,
    private val retry: RetryPolicy?,
    private val timeout: Duration?,
) : AnnotatedNode() {
    override fun declare(
        sink: NodeSink<Any?>,
        instance: Any,
    ) {
        val node = sink.step(name) { body.callSuspend(instance, this) }
        compensation?.let { undo -> node compensate { undo.callSuspend(instance, this) } }
        retry?.let { node retry it }
        timeout?.let { node timeout it }
    }
}

internal class AwaitNode(
    override val order: Int,
    override val name: String,
    private val signal: Signal<Any?>,
    private val body: KFunction<*>,
    private val deadline: Duration?,
) : AnnotatedNode() {
    override fun declare(
        sink: NodeSink<Any?>,
        instance: Any,
    ) {
        val node = sink.await(signal, name) { payload -> body.callSuspend(instance, this, payload) }
        deadline?.let { node within it }
    }
}

internal class SleepNode(
    override val order: Int,
    override val name: String,
    private val body: KFunction<*>,
) : AnnotatedNode() {
    override fun declare(
        sink: NodeSink<Any?>,
        instance: Any,
    ) {
        // `call`, not `callSuspend`: the DSL's duration block does not suspend, and the reader
        // refuses a suspending @Sleep rather than blocking a thread to honour it here.
        sink.sleep(name) { body.call(instance, this) as Duration }
    }
}

internal class ChildNode(
    override val order: Int,
    override val name: String,
    private val child: Workflow<Any?>,
    private val start: KFunction<*>,
    private val body: KFunction<*>,
    private val deadline: Duration?,
) : AnnotatedNode() {
    override fun declare(
        sink: NodeSink<Any?>,
        instance: Any,
    ) {
        // `call` for the starting context and `callSuspend` for the result: the first computes a
        // value the way @Sleep computes a duration, the second is the parent carrying on and may do
        // anything a step may do.
        val node =
            sink.child(name, child, with = { start.call(instance, this) }) { done ->
                body.callSuspend(instance, this, done)
            }
        deadline?.let { node within it }
    }
}
