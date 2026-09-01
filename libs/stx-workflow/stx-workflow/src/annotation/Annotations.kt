package com.softistx.workflow.annotation

/**
 * Marks a class as a workflow declaration, to be turned into a `Workflow<C>` by `workflowOf`.
 *
 * ```kotlin
 * @WorkflowDefinition("checkout")
 * class CheckoutWorkflow(private val stock: Stock, private val payments: Payments) {
 *
 *     @Step(1)
 *     suspend fun StepScope<Checkout>.reserve(): Checkout =
 *         context.copy(reservationId = stock.reserve(context.items))
 *
 *     @Compensate("reserve")
 *     suspend fun StepScope<Checkout>.releaseReservation() = stock.release(context.reservationId!!)
 * }
 * ```
 *
 * The collaborators are the class's constructor — the same rule the DSL has, where a step body
 * closes over what it needs. The instance is built once, at startup, and shared by every run.
 *
 * [name] has no default on purpose. It is what the store records and what an engine looks a
 * definition up by, so renaming it orphans every instance still in flight; defaulting it to the
 * class's simple name would make a rename in the IDE do that silently.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class WorkflowDefinition(
    val name: String,
)

/**
 * One step, and where it comes in the order.
 *
 * The function is a **member extension on `StepScope<C>`** returning `C`, so its body is written
 * exactly as a `step { }` block is:
 *
 * ```kotlin
 * @Step(2)
 * suspend fun StepScope<Checkout>.charge(): Checkout =
 *     context.copy(chargeId = payments.charge(context.card, key = idempotencyKey))
 * ```
 *
 * **[order] is required, and it is not bureaucracy.** The JVM makes no promise about the order
 * `Class.getDeclaredMethods` returns — it is explicitly "in no particular order" — so a workflow
 * that took its step order from the source would be a workflow whose steps can be reordered by a
 * compiler upgrade, on a class whose whole contract is that things happen in sequence. The numbers
 * need not be contiguous; only their relative order is read.
 *
 * [name] defaults to the function's, and is what appears in the journal. Renaming the function
 * therefore renames the node, which an in-flight instance will not recognise — pin it here when
 * that matters.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Step(
    val order: Int,
    val name: String = "",
)

/**
 * How to undo the step named [step], when a later node fails and the workflow unwinds.
 *
 * ```kotlin
 * @Compensate("reserve")
 * suspend fun StepScope<Checkout>.releaseReservation() = stock.release(context.reservationId!!)
 * ```
 *
 * It names the **step**, not the function, because that is the name the journal holds and the name
 * an operator reads. A compensation for a step that does not exist is refused when the workflow is
 * built, which is where a typo should surface rather than in the middle of an unwind.
 *
 * The function returns nothing and takes no order: a compensation runs when the unwind reaches its
 * step, newest first, and never in any other sequence.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Compensate(
    val step: String,
)

/**
 * Waits for a signal before the workflow goes on. The annotated function is the `await` block.
 *
 * ```kotlin
 * @Await(3, signal = "approval", withinMillis = 24 * 60 * 60 * 1000)
 * suspend fun StepScope<Refund>.approved(approval: Approval): Refund =
 *     context.copy(approvedBy = approval.by)
 * ```
 *
 * The single parameter is the payload, and its type is what the delivered signal is decoded as —
 * so `engine.signal(id, signal<Approval>("approval"), payload)` and this declaration agree by
 * construction rather than by convention.
 *
 * [withinMillis] of zero means no deadline: the instance waits indefinitely, out of the store's
 * due-time index, until somebody signals or cancels it. A deadline that passes **fails** the wait
 * and unwinds the workflow.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Await(
    val order: Int,
    val signal: String,
    val name: String = "",
    val withinMillis: Long = 0,
)

/**
 * Pauses until a moment the function computes. The function returns a `Duration` and does not suspend.
 *
 * ```kotlin
 * @Sleep(4)
 * fun StepScope<Refund>.settlement(): Duration = 2.days
 * ```
 *
 * A duration rather than a `millis` on the annotation, because an annotation cannot hold a
 * `Duration` and a `Long` on one is a unit waiting to be misread. The function is evaluated once,
 * when the instance parks; see `sleep` in the DSL for what that buys.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Sleep(
    val order: Int,
    val name: String = "",
)

/**
 * The retry policy for the [Step] this is on.
 *
 * ```kotlin
 * @Step(2)
 * @Retry(times = 3, delayMillis = 200, factor = 2.0, maxMillis = 5_000)
 * suspend fun StepScope<Checkout>.charge(): Checkout = …
 * ```
 *
 * A [factor] of 1.0 — the default — is a fixed delay; anything larger is exponential, capped at
 * [maxMillis]. It belongs on a step and nowhere else: an [Await] has nothing to retry, since the
 * payload it failed on is the one that already arrived, and a [Sleep] computes a duration. The declared half of "do not try this again" has no annotation: `unless { }` takes a
 * predicate, which an annotation cannot hold, so an annotated workflow says it by throwing
 * `NonRetryableException` from the step. That is the half that knows, anyway.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Retry(
    val times: Int,
    val delayMillis: Long = 100,
    val factor: Double = 1.0,
    val maxMillis: Long = 30_000,
)

/**
 * A deadline on **one attempt** of the node this is on.
 *
 * A step with three attempts and a ten-second timeout may take thirty seconds plus its backoff:
 * timing the node as a whole would mean each retry inheriting what the last one spent, so the final
 * attempt — the one that matters — always gets the least time.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Timeout(
    val millis: Long,
)

/**
 * Runs another workflow and waits for it. The annotated function builds the child's starting context.
 *
 * ```kotlin
 * @Child(3, workflow = "fulfilment", withinMillis = 3 * 24 * 60 * 60 * 1000)
 * fun StepScope<Order>.fulfil(): Fulfilment = Fulfilment(items = context.items)
 *
 * @ChildResult("fulfil")
 * suspend fun StepScope<Order>.fulfilled(done: Fulfilment): Order = context.copy(trackingId = done.booking)
 * ```
 *
 * Two functions, for the same reason `@Step` and `@Compensate` are two: they run at different
 * moments, months apart in the cases this exists for, and one of them is handed something the other
 * has never seen.
 *
 * [workflow] names the child **by its workflow name**, because an annotation cannot hold a
 * `Workflow<D>`. The declaration is passed to `workflowOf(definition, fulfilment)`, and a name that
 * is not among them is refused when the parent is built — beside the mistake, rather than on the
 * first instance that reaches the node.
 *
 * The function does not suspend: it computes the child's context and nothing else, exactly as
 * `@Sleep` computes a duration. Its return type is checked against the named workflow's context.
 *
 * [withinMillis] of zero means no deadline. A deadline that passes **fails** the node and unwinds
 * the parent — which still takes the child back, because a child node owes its compensation from the
 * moment it started the instance rather than from the moment it succeeded.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Child(
    val order: Int,
    val workflow: String,
    val name: String = "",
    val withinMillis: Long = 0,
)

/**
 * What the child named [child] does to the parent's context when it finishes.
 *
 * ```kotlin
 * @ChildResult("fulfil")
 * suspend fun StepScope<Order>.fulfilled(done: Fulfilment): Order = context.copy(trackingId = done.booking)
 * ```
 *
 * It names the **node**, not the function, for the same reason `@Compensate` does: that is the name
 * the journal holds and the name an operator reads. The single parameter is the child's final
 * context, and its type is checked against the child workflow's — so the two declarations agree by
 * construction rather than by convention.
 *
 * It takes no order. It runs when its child finishes, and never in any other sequence.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ChildResult(
    val child: String,
)
