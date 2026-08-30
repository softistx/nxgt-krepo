package com.strange.spring.data.mongo.template

import com.mongodb.reactivestreams.client.MongoClients
import com.strange.spring.data.mongo.convert.stxMongoConversions
import com.strange.testing.containers.TestNames
import com.strange.testing.containers.mongoContainer
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

/**
 * A `ReactiveMongoTemplate` over whatever Mongo this machine has — the workspace's replica set when
 * `MONGO_TEST_URI` names it, a container started once for the run otherwise.
 *
 * Built by hand rather than through the auto-configuration, because these specs are about `findPage`
 * and not about Spring Boot's wiring; the auto-configuration has `ApplicationContextRunner` specs
 * and needs no server for them.
 */
internal object SpringMongo {
    private val mongo = mongoContainer()

    /** A database per call, and one no other run will pick — see [TestNames]. */
    private val databases = TestNames("stx_spring_test", separator = "_")

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
        val client = MongoClients.create(mongo.requireEndpoint())
        val name = databases.next()
        try {
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
