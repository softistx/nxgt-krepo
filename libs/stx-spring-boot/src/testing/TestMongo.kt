package com.strange.spring.testing

import com.strange.testing.containers.TestNames
import com.strange.testing.containers.mongoContainer

/**
 * The MongoDB an application's specs talk to, declared once for the whole test run.
 *
 * **Declared, not started.** `mongoContainer()` only names the service: `ContainerService.endpoint`
 * is a `lazy`, so loading this file starts nothing. The first read is [MongoTestConfiguration]'s
 * bean, which Spring calls while building the context for the first spec that needs one — so a run
 * whose specs are all skipped never pays for a container, and the one that does starts it once for
 * every spec in the module. It is stopped when the JVM exits, by `ContainerService`'s own hook.
 *
 * Where it comes from is `stx-testing`'s question, not this module's: `MONGO_TEST_URI` when a server
 * is already up, a container otherwise, and neither means [available] is `false`.
 */
object TestMongo {
    internal val service = mongoContainer()

    /** Whether a spec that needs MongoDB can run. */
    val available: Boolean get() = service.available

    /** Where this one ended up — for a skip that has to say why. */
    fun describe(): String = service.describe()
}

/**
 * A MongoDB address nothing listens on.
 *
 * For the two cases that must *not* reach a server: the fallback in [MongoTestConfiguration] when
 * this machine has neither Docker nor `MONGO_TEST_URI`, and a wiring spec that only wants a
 * `MongoClient` to exist. Port 1 is privileged and unbound, so a connection is refused immediately
 * rather than hanging.
 *
 * **Not `localhost:27017`.** Creating a client opens no connection *in the calling thread*, which is
 * what makes a wiring spec cheap — but the driver starts a cluster monitor that connects on its own,
 * and on a developer machine 27017 is a real server that answers. A spec written to need no server
 * then quietly has one, and talks to it.
 */
internal const val UNREACHABLE_MONGO = "mongodb://127.0.0.1:1"

/**
 * Whether a spec that needs MongoDB can run.
 *
 * Every feature that touches a server is `.config(enabled = mongoAvailable)`, so a machine without
 * Docker and without `MONGO_TEST_URI` reports skipped tests rather than failing a build over
 * something that is not the code.
 */
val mongoAvailable: Boolean get() = TestMongo.available

/**
 * [name] with this run's suffix on it — the database an application's specs actually get.
 *
 * `spring.mongodb.database` names the *prefix*, not the database. The suffix is
 * `stx-testing`'s [TestNames.RUN], for the reason every harness in this repo now carries one: a
 * fixed name is shared with whatever else is running, and these specs empty collections. Two suites
 * at once against a server `MONGO_TEST_URI` names would clear each other's documents, and a suite
 * run twice against one would find its migrations already recorded — so the seed a migration writes
 * would not be rewritten after the first run cleared it, and only the second run would fail.
 *
 * Against a container none of that arises, because the server is thrown away at JVM exit. This is for
 * the override arm, which is the arm a developer with the workspace up is actually on.
 */
fun testDatabase(name: String): String = "${name}_${TestNames.RUN}"

/**
 * [this] with its database replaced by [name].
 *
 * **The endpoint cannot be taken as given.** `MongoDBContainer` hands back a URL ending in `/test`,
 * and a `MONGO_TEST_URI` naming a shared replica set usually ends in no database at all — so a suite
 * that used either as-is would land every run in one database while looking isolated, including the
 * one a `./kotlin run` writes to. What surfaced that was a manual run against the same server leaving
 * rows a paging scenario then counted.
 *
 * Splicing the name in here rather than setting a property and hoping is also what makes it mean the
 * same thing across Boot versions: the database is in the connection string, which is the one place
 * every reading of it agrees on. `MongoTestConfiguration` has what happened when that was left to a
 * property.
 */
internal fun String.withDatabase(name: String): String {
    val query = substringAfter("?", "").let { if (it.isEmpty()) "" else "?$it" }
    val base = substringBefore("?").trimEnd('/')
    // "mongodb://host:port" has two slashes; a third one starts the database path.
    val host = if (base.count { it == '/' } > 2) base.substringBeforeLast('/') else base
    return "$host/$name$query"
}
