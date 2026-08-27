package com.strange.testing.containers

import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A backing service the integration tests need, declared once and started at most once.
 *
 * ```kotlin
 * private val mongo = mongoContainer()
 *
 * feature("reading").config(enabled = mongo.available) { … }
 * ```
 *
 * **Where it comes from is resolved, not assumed.** If the environment variable this was declared
 * with names a server, that server is used and no container is started; otherwise a container is.
 * A machine with neither reports [available] as `false`, which is what keeps a spec *skipped* rather
 * than failing a build over something that is not the code.
 *
 * That order is deliberate. A container is the default because a test suite that only passes on a
 * machine with the right daemons already running is a test suite that passes for the wrong reason.
 * The override stays because a developer with the workspace already up should not pay a container
 * start per run, and because it is the seam CI uses to point at a service it provisioned itself.
 *
 * **Started once per JVM, not once per spec.** A container start is seconds; a suite of twenty
 * specs restarting one would spend minutes doing it. The isolation each spec needs is a database or
 * a namespace of its own inside the shared container — cheap, and the same discipline the specs
 * already follow against the workspace's own servers.
 */
class ContainerService<C : GenericContainer<*>, E : Any> internal constructor(
    private val name: String,
    private val reusing: String,
    private val fromEnvironment: () -> E?,
    private val create: () -> C,
    private val fromContainer: (C) -> E,
) {
    private var container: C? = null

    /** Recorded when the endpoint is resolved, so [describe] stays truthful after [stop]. */
    private var origin = Origin.NONE

    /**
     * Where the service is, or `null` when there is no way to reach one.
     *
     * Resolving happens on first read and never again — `lazy` is synchronized, so concurrent specs
     * asking at once still start one container between them.
     */
    val endpoint: E? by lazy { resolve() }

    /** Whether a spec that needs this service can run. */
    val available: Boolean get() = endpoint != null

    /** Where this one ended up, for a log line or a failure that needs to say why it skipped. */
    fun describe(): String =
        when (origin.also { endpoint }) {
            Origin.REUSED -> "$name: reusing the server named by $reusing"
            Origin.CONTAINER -> "$name: a container started for this run"
            Origin.NONE -> "$name: unavailable — $reusing is unset and Docker is not reachable"
        }

    /**
     * Stops the container, if this started one.
     *
     * Called for every declared service when the JVM exits, so a suite needs no teardown of its own.
     * Testcontainers' Ryuk sidecar is the backstop underneath: it removes what this run created even
     * when the JVM is killed and no hook gets to run.
     */
    fun stop() {
        container?.let { runCatching { it.stop() } }
        container = null
    }

    private fun resolve(): E? {
        fromEnvironment()?.let {
            origin = Origin.REUSED
            return it
        }
        if (!dockerReachable()) return null

        return runCatching {
            create().apply {
                start()
                container = this
                origin = Origin.CONTAINER
                Registry.add(this@ContainerService)
            }
        }.map(fromContainer).getOrNull()
    }

    /** Where the endpoint came from. Not the same question as whether a container is still running. */
    private enum class Origin { REUSED, CONTAINER, NONE }

    companion object {
        /**
         * Declares a service whose connection details are one string in one variable.
         *
         * [reusing] names the environment variable that points at an already-running server;
         * [create] builds the container used when it is unset; [endpointOf] reads the connection
         * string out of the started container. This is the shape every backend but MinIO has.
         */
        fun <C : GenericContainer<*>> declare(
            name: String,
            reusing: String,
            create: () -> C,
            endpointOf: (C) -> String,
        ): ContainerService<C, String> =
            declare(
                name = name,
                reusing = reusing,
                fromEnvironment = { System.getenv(reusing)?.takeIf(String::isNotBlank) },
                create = create,
                fromContainer = endpointOf,
            )

        /**
         * The same, for a service that needs more than a URI.
         *
         * MinIO is the reason: an endpoint is useless without the key pair, and the two have to be
         * resolved *together* — a run with `MINIO_TEST_ENDPOINT` set and no credentials must fall
         * through to a container rather than reach half of somebody's server. [fromEnvironment]
         * returns null unless it can supply the whole value, and [reusing] is then a description of
         * what it reads rather than one variable's name.
         */
        fun <C : GenericContainer<*>, E : Any> declare(
            name: String,
            reusing: String,
            fromEnvironment: () -> E?,
            create: () -> C,
            fromContainer: (C) -> E,
        ): ContainerService<C, E> = ContainerService(name, reusing, fromEnvironment, create, fromContainer)

        /**
         * Whether this machine has a Docker daemon at all.
         *
         * Asked before building a container, because the failure path without it is slow and loud
         * and the answer here is neither.
         */
        internal fun dockerReachable(): Boolean = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
    }
}

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
