package com.softistx.spring.testing

import com.mongodb.reactivestreams.client.MongoClients
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drops the database this run made, when the run ends.
 *
 * **The server is usually not ours.** A `MONGO_TEST_URI` names the workspace's own replica set, and
 * [testDatabase] gives every run a name of its own — so without this, a developer's Mongo collects one
 * `spring_orders_test_*` per `./kotlin test`, forever. Against a container this is a no-op that costs
 * a connection, because the whole server is discarded at JVM exit anyway; it is the override arm that
 * needs it. Every other Mongo harness here already leaves the server as it found it — `SpringMongo`
 * in a `finally`, `MongoTestCluster` after each database — and this is that same rule for the one
 * place where the database outlives any single spec.
 *
 * **A shutdown hook and not a `DisposableBean`.** The database belongs to the *run*, not to a context:
 * a module's specs share one name, and Spring's context cache may evict and close one context while
 * another is still using it. A bean destroyed then would drop a database still being written to.
 *
 * Failures are swallowed on purpose. The unreachable-URI arm has nothing to drop, a server may have
 * gone away first, and neither is a reason to fail a build that has already reported its results.
 */
internal object MongoTestCleanup {
    private val registered = AtomicBoolean()

    /**
     * Arranges for [database] at [uri] to be dropped at JVM exit. Idempotent — called from the
     * connection-details bean, which a module with several contexts builds several times.
     */
    fun dropAtExit(
        uri: String,
        database: String,
    ) {
        if (!TestMongo.available || !registered.compareAndSet(false, true)) return

        // `unstarted`, never `startVirtualThread`: the latter starts on the spot, so the hook is
        // registered already-dead or refused, and the JVM says nothing either way.
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted { drop(uri, database) })
    }

    /**
     * Drops [database] at [uri], now.
     *
     * Separate from [dropAtExit] so that it can be specced: a shutdown hook runs after the last
     * assertion, so what it does is otherwise only observable by hand. `MongoTestCleanupTest` calls
     * this one.
     */
    fun drop(
        uri: String,
        database: String,
    ) {
        runCatching {
            MongoClients.create(uri).use { client ->
                runBlocking { client.getDatabase(database).drop().awaitFirstOrNull() }
            }
        }
    }
}
