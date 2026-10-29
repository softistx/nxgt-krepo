package com.softistx.testing.containers

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every service that started a container, so the JVM can stop them on the way out.
 *
 * **A virtual thread, and an `unstarted` one.** `Runtime.addShutdownHook` takes a `Thread`, which is
 * the whole reason there is a thread here at all — nothing in this file suspends, and a coroutine
 * cannot be handed to that API. Given that a thread is forced, it is a virtual one: a few hundred
 * bytes against a megabyte of committed stack, for something that exists only to block on Docker.
 *
 * `Thread.startVirtualThread` would be the wrong half of the API and fails *silently*, which is why
 * this comment exists. It starts the thread immediately, so the body runs here at class-init with
 * nothing registered yet, and the hook is then an already-terminated thread — at exit
 * `ApplicationShutdownHooks` calls `start()` on it, gets an `IllegalThreadStateException`, and
 * `Shutdown.runHooks` swallows it. Nothing is torn down and nothing says so.
 *
 * The list is plain Java concurrency rather than this repo's coroutine primitives, and deliberately:
 * a shutdown hook has no scope to suspend in, and the list is written once per service and read once
 * per run.
 */
internal object Registry {
    private val started = CopyOnWriteArrayList<ContainerService<*, *>>()

    /** Registered, never started here. Exposed so a spec can assert both of those things. */
    val hook: Thread = Thread.ofVirtual().name("testcontainers-teardown").unstarted(::teardown)

    init {
        Runtime.getRuntime().addShutdownHook(hook)
    }

    fun add(service: ContainerService<*, *>) {
        started += service
    }

    /**
     * Stops every container this run started.
     *
     * Serial, and measured rather than assumed: with four backends this is four `docker stop` round
     * trips at exit. Fanning them out over virtual threads is a two-line change if that ever costs
     * enough to matter.
     */
    internal fun teardown() {
        started.forEach { it.stop() }
    }
}
