package com.strange.spring.data.mongo.config

import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Creates the indexes the mapped entities declare, once, after the application is ready.
 *
 * **Nothing is ever dropped.** The version this replaces dropped every index on every collection and
 * rebuilt them at each startup, which is an outage waiting for a large collection: while an index is
 * being rebuilt, every query that used it collection-scans, and a rolling deploy does that once per
 * instance. Creating an index that already exists is a no-op in Mongo, so create-only is idempotent
 * and safe on every boot — and an index that is no longer declared is left alone, because deciding
 * it is unused is a migration's job and not a startup's.
 *
 * A failure on one collection is logged and the rest continue. An index that cannot be built — a
 * unique index over data that is not unique — should not stop a service from starting, and the
 * alternative is an application that boots on an empty database and refuses to on a real one.
 */
class IndexInitializer(
    private val template: ReactiveMongoTemplate,
) {
    private val log = LoggerFactory.getLogger(IndexInitializer::class.java)

    @EventListener(ApplicationReadyEvent::class)
    suspend fun createIndexes() {
        val context = template.converter.mappingContext
        val resolver = MongoPersistentEntityIndexResolver(context)

        context
            .persistentEntities
            .filter { it.isAnnotationPresent(Document::class.java) }
            .forEach { entity ->
                val definitions = resolver.resolveIndexFor(entity.type).toList()
                if (definitions.isEmpty()) return@forEach

                val operations = template.indexOps(entity.type)
                definitions.forEach { definition ->
                    runCatching { operations.createIndex(definition).awaitFirstOrNull() }
                        .onFailure {
                            log.warn(
                                "stx.data.mongo: could not create an index on {}: {}",
                                entity.type.simpleName,
                                it.message,
                            )
                        }
                }
                log.debug("stx.data.mongo: {} index(es) ensured on {}", definitions.size, entity.type.simpleName)
            }
    }
}
