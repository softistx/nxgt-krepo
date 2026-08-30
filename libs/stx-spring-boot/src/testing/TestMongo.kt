package com.strange.spring.testing

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
 * Whether a spec that needs MongoDB can run.
 *
 * Every feature that touches a server is `.config(enabled = mongoAvailable)`, so a machine without
 * Docker and without `MONGO_TEST_URI` reports skipped tests rather than failing a build over
 * something that is not the code.
 */
val mongoAvailable: Boolean get() = TestMongo.available

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
