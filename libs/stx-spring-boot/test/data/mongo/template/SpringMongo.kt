package com.strange.spring.data.mongo.template

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.strange.spring.data.mongo.convert.stxMongoConversions
import com.strange.testing.containers.mongoContainer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * A `ReactiveMongoTemplate` over whatever Mongo this machine has — the workspace's replica set when
 * `MONGO_TEST_URI` names it, a container started once for the run otherwise.
 *
 * Built by hand rather than through the auto-configuration, because these specs are about `findPage`
 * and not about Spring Boot's wiring; the auto-configuration has `ApplicationContextRunner` specs
 * and needs no server for them.
 */
private const val PREFIX = "stx_spring_"

internal object SpringMongo {
    private val mongo = mongoContainer()

    private val databases = AtomicInteger()

    /** Reachable at all. A spec that cannot reach a server reports skipped rather than failing. */
    val available: Boolean get() = mongo.available

    /**
     * A template on a database of its own, dropped when [block] returns.
     *
     * The drop is the point. This server is usually not ours — `MONGO_TEST_URI` names the
     * workspace's own replica set — and a run that reuses a server has to leave it as it found it.
     * It is also what keeps two specs from seeing each other's documents, which matters more here
     * than usual: every scenario seeds the same six notes into the same collection name.
     */
    suspend fun <T> withTemplate(block: suspend (ReactiveMongoTemplate) -> T): T {
        val uri = requireNotNull(mongo.endpoint) { mongo.describe() }
        val name = "$PREFIX${databases.incrementAndGet()}"
        val client = MongoClients.create(uri)
        try {
            sweep(client)
            val context = MongoMappingContext().apply { afterPropertiesSet() }
            val converter =
                MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context).apply {
                    setCustomConversions(stxMongoConversions())
                    afterPropertiesSet()
                }
            return block(ReactiveMongoTemplate(SimpleReactiveMongoDatabaseFactory(client, name), converter))
        } finally {
            // In `finally`, so a failing assertion still leaves the server as it was found.
            runCatching { client.getDatabase(name).drop().awaitFirstOrNull() }
            client.close()
        }
    }
}

private var swept = false

/**
 * Drops any database this harness left behind, once per run, before the first spec uses one.
 *
 * Cleaning up afterwards is the rule; this is what makes the rule survive a run that did not get
 * to. Without it a crashed run leaves `stx_spring_1` full of notes, the next run's counter starts
 * at 1 again, and every insert fails on a duplicate `_id` — which is how this came to be written.
 *
 * Only names this module issued, on a server that is usually somebody else's.
 */
private suspend fun sweep(client: MongoClient) {
    if (swept) return
    swept = true
    runCatching {
        client
            .listDatabaseNames()
            .asFlow()
            .toList()
            .filter { it.startsWith(PREFIX) }
            .forEach { client.getDatabase(it).drop().awaitFirstOrNull() }
    }
}
