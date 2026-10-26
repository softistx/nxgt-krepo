package com.softistx.spring.data.mongo.audit

import com.softistx.common.coroutines.Mailbox
import com.softistx.common.lifecycle.CloseGuard
import com.softistx.spring.security.currentUserName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.bson.Document
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent
import org.springframework.data.mongodb.core.mapping.event.ReactiveAfterSaveCallback
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * Turns Spring Data's save and delete into audit entries.
 *
 * **The work is launched on an injected scope, never `GlobalScope`.** A trail written on a scope
 * nobody owns keeps running through shutdown and is cancelled by the process exiting, which loses
 * whatever was in flight; and nothing in a test can wait for it. The scope is a bean, so an
 * application can replace it, and the auto-configuration's own is cancelled with the context.
 *
 * **The write is asynchronous on purpose.** Auditing runs after the save has already happened, so
 * failing it cannot undo anything — and blocking the request thread on a second write to make an
 * audit trail feel synchronous costs every caller latency for a record nobody is waiting on.
 * The consequence is worth stating plainly: an entry can be lost if the process dies in the window
 * between the save and the append.
 *
 * **A save is observed through [ReactiveAfterSaveCallback], not the `AfterSaveEvent`.** An event
 * listener runs outside the save's Reactor context, so the security context is not there to read
 * and every author came out empty. The callback is a `Publisher` inside the save's own chain: the
 * author is read there, while the context exists, and handed to the write.
 *
 * **Entries are written one at a time, in the order they were posted.** One consumer owns the
 * [Mailbox], so two rapid saves of one document are diffed against each other in save order rather
 * than racing for the same next version. Closing the listener (Spring does, at context shutdown)
 * lets the queue drain for up to five seconds before the scope is cancelled. A delete has no after-callback in Spring Data's reactive
 * template, so it still arrives as an event and carries no author.
 */
class AuditListener(
    private val trailOf: () -> AuditTrail,
    private val scope: CoroutineScope,
) : ReactiveAfterSaveCallback<Any>,
    AutoCloseable {
    constructor(trail: AuditTrail, scope: CoroutineScope) : this({ trail }, scope)

    private val trail: AuditTrail get() = trailOf()

    private val log = LoggerFactory.getLogger(AuditListener::class.java)
    private val writes = Mailbox<AuditWrite>()
    private val queued = AtomicInteger()
    private val guard = CloseGuard()

    private val consumer: Job =
        scope.launch(CoroutineName("stx-audit")) {
            writes.consume { write ->
                try {
                    write.block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    log.error("stx.data.mongo.audit: could not record a {}", write.what, failure)
                } finally {
                    queued.decrementAndGet()
                }
            }
        }

    /**
     * Stops taking writes and gives the ones already queued [DRAIN_TIMEOUT] to land.
     *
     * Called by Spring when the context closes, before the scope is cancelled (the listener depends
     * on it). What is still queued when the time is up is lost, and the count is logged.
     */
    override fun close() =
        guard.once {
            writes.close()
            val drained = runBlocking { withTimeoutOrNull(DRAIN_TIMEOUT) { consumer.join() } } != null
            if (!drained) {
                log.warn("stx.data.mongo.audit: {} audit write(s) still queued after {}, dropped", queued.get(), DRAIN_TIMEOUT)
                consumer.cancel()
            }
        }

    private fun post(write: AuditWrite) {
        queued.incrementAndGet()
        if (!writes.post(write)) queued.decrementAndGet()
    }

    override fun onAfterSave(
        entity: Any,
        document: Document,
        collection: String,
    ): Publisher<Any> {
        if (!entity::class.java.isAnnotationPresent(Auditable::class.java)) return Mono.just(entity)

        // The author is read here, in the save's own chain, because this is the only place the
        // security context is reachable; it never fails the save it describes.
        return authorOfSave()
            .doOnNext { author -> post(AuditWrite("save") { trail.commit(entity, collection, author) }) }
            .thenReturn(entity)
    }

    /**
     * The signed-in user's name, or empty. Spring Security is an optional dependency, so a missing
     * class is the same answer as nobody being signed in. The security types stay in
     * [currentUserName]: none may appear in this class's own members, which Spring reflects over.
     */
    private fun authorOfSave(): Mono<String> =
        try {
            currentUserName()
        } catch (_: NoClassDefFoundError) {
            Mono.just("")
        }

    @EventListener
    fun onAfterDelete(event: AfterDeleteEvent<*>) {
        if (event.type?.isAnnotationPresent(Auditable::class.java) != true) return

        val oid =
            event.document
                ?.get("_id")
                ?.toString()
                .orEmpty()
        if (oid.isBlank()) return

        post(AuditWrite("delete") { trail.terminate(oid, event.collectionName.orEmpty(), null) })
    }
}

private val DRAIN_TIMEOUT = 5.seconds

/** One queued audit write, and what to call it in the log. */
private class AuditWrite(
    val what: String,
    val block: suspend () -> Unit,
)
