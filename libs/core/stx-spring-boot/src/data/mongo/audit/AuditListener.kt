package com.softistx.spring.data.mongo.audit

import com.softistx.spring.security.currentUser
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent

/**
 * Turns Spring Data's save and delete events into audit entries.
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
 */
class AuditListener(
    private val trail: AuditTrail,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(AuditListener::class.java)

    @EventListener
    fun onAfterSave(event: AfterSaveEvent<*>) {
        val source = event.source
        if (!source::class.java.isAnnotationPresent(Auditable::class.java)) return

        record("save") { trail.commit(source, event.collectionName.orEmpty(), author()) }
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

        record("delete") { trail.terminate(oid, event.collectionName.orEmpty(), author()) }
    }

    /**
     * Runs [block] on the audit scope, and lets nothing out.
     *
     * A failure here must not surface as a failed request: the write it describes has already
     * happened, so an exception would report a failure to a caller whose work succeeded.
     */
    private fun record(
        what: String,
        block: suspend () -> Unit,
    ) {
        scope.launch(CoroutineName("stx-audit-$what")) {
            runCatching { block() }
                .onFailure { log.error("stx.data.mongo.audit: could not record a {}", what, it) }
        }
    }

    /**
     * Who is making the request, read from the reactive security context.
     *
     * Null when nobody is — a background job, a migration, a startup task. An empty author is
     * better than a wrong one, and better than refusing to record the change at all.
     */
    private suspend fun author(): String? = runCatching { currentUser()?.username }.getOrNull()
}
