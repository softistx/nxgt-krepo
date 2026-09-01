package com.softistx.testing.containers

import java.util.concurrent.atomic.AtomicInteger

/**
 * Names for the thing a spec gets to itself inside a shared server — a database, a schema, a topic,
 * a bucket, a key namespace.
 *
 * A container is started once per run and shared, so isolation is not a server each but a *namespace*
 * each inside one. Every library here needs the same thing and five of them had invented it
 * separately, with five different prefixes and, more to the point, three different answers to what
 * happens when a run does not finish.
 *
 * ```kotlin
 * private val databases = TestNames("stx_mongo_test", separator = "_")
 *
 * val database = client.getDatabase(databases.next())
 * ```
 *
 * **The run suffix is the part that matters.** A plain counter restarts at 1 in the next JVM, so a
 * run that crashed leaves `stx_spring_1` full of documents and the next run's first insert fails on a
 * duplicate `_id` — which is a real failure this repo shipped, and which was patched by sweeping
 * every leftover database before the first spec. That sweep could not tell a crashed run's leftovers
 * from a *concurrent* run's, so two suites at once would delete each other's data. Naming what cannot
 * collide in the first place needs neither.
 *
 * What it does not do is clean up: a crashed run still leaves its namespace behind on a server named
 * by `MONGO_TEST_URI` and friends. That is the trade, and it is the cheap side of it — against a
 * container, which is the default, the whole server is thrown away at JVM exit.
 *
 * [separator] is a parameter because the namespaces disagree: Redis keys read `a:b`, SQL identifiers
 * and Mongo databases take `_`, Kafka topics and object-store buckets take `-`. The suffix itself is
 * lowercase alphanumeric so that it is legal in all five — a bucket name may not carry an underscore
 * and an SQL identifier may not start with a digit, and a prefix beginning with a letter covers both.
 */
class TestNames(
    private val prefix: String,
    private val separator: String = "-",
) {
    private val counter = AtomicInteger()

    /** The next one. Unique within this run by the counter, and across runs by [RUN]. */
    fun next(): String = "$prefix$separator${counter.incrementAndGet()}$separator$RUN"

    companion object {
        /**
         * This run, in base 36 — short enough to leave a Mongo database name inside its 63 bytes.
         *
         * `nanoTime` rather than a UUID because it needs to be unique against the *other runs on this
         * machine*, not globally, and a shorter name is one that fits in every namespace's limit.
         *
         * Public for the one namespace this class cannot shape: an AMQP name is dotted and wants the
         * caller's own prefix in the middle, so `AmqpTestBroker` builds its own around this.
         */
        val RUN: String = System.nanoTime().toString(RADIX).takeLast(RUN_LENGTH)

        private const val RADIX = 36
        private const val RUN_LENGTH = 8
    }
}
