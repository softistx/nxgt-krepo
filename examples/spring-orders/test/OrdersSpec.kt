package com.strange.example.orders

import com.strange.testing.containers.mongoContainer
import io.kotest.core.spec.style.FeatureSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * The application, started the way Spring starts it.
 *
 * **A spec here declares what it needs and Spring provides it** — `@SpringBootTest` builds the
 * context, binds the port `testResources/application-test.yaml` names, and hands the beans a spec
 * asks for to its constructor. There is no `SpringApplicationBuilder` to write, no context to close
 * and no port to discover, and the context is cached across the specs that share this configuration,
 * so the application starts once for the module rather than once per spec. It is `nxgt-rest`'s
 * pattern; `io.kotest.provided.ProjectConfig` registers the extension that makes it work.
 *
 * The base class exists for the one thing an annotation cannot say: where MongoDB is. That is
 * resolved at run time — a container, or the server `MONGO_TEST_URI` names — so it arrives through
 * [DynamicPropertySource], which is read while the context is being built and outranks every
 * property source there is.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
abstract class OrdersSpec(
    body: FeatureSpec.() -> Unit,
) : FeatureSpec(body) {
    companion object {
        /** Where the application answers under the `test` profile — `server.port` there, and here. */
        const val BASE_URL = "http://localhost:8088"

        /**
         * MongoDB, resolved the first time something asks for it and not before.
         *
         * `mongoContainer()` only *declares* the service: `ContainerService.endpoint` is a `lazy`,
         * so nothing is started by this file being loaded. The first read is [mongoUri] below, which
         * Spring calls while building the context for the first spec that runs — so a container
         * starts when a spec that needs one is reached, and a run whose specs are all skipped starts
         * nothing at all. It is stopped when the JVM exits, by `ContainerService`'s own hook.
         */
        val mongo = mongoContainer()

        /** The database the specs share. Their own, and never the one a `./kotlin run` writes to. */
        private const val DATABASE = "spring_orders_test"

        /**
         * Where MongoDB is, read while the context is being built.
         *
         * The fallback is a port nothing listens on, and is only ever reached on a machine with
         * neither Docker nor `MONGO_TEST_URI`. Registering nothing would leave `application.yaml`'s
         * URI in place and point a suite at the demo's own database; registering an unreachable one
         * lets the context start and every feature report skipped, which is what
         * `.config(enabled = mongo.available)` is for.
         */
        @JvmStatic
        @DynamicPropertySource
        fun mongoUri(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") {
                mongo.endpoint?.withDatabase(DATABASE) ?: "mongodb://127.0.0.1:1/$DATABASE"
            }
        }
    }
}

/**
 * [this] with its database replaced by [name].
 *
 * **Not `spring.data.mongodb.database`.** Boot reads that property only when it is building a
 * connection string from `host`/`port`; once `spring.data.mongodb.uri` is set the database comes
 * from the URI and the property is ignored, silently. `MongoDBContainer` hands back a URL ending in
 * `/test` and a `MONGO_TEST_URI` naming the workspace's replica set usually ends in no database at
 * all — so both spellings were landing every run in one shared database, and the isolation these
 * specs claim was not happening. What surfaced it was a manual `./kotlin run` against the same
 * server leaving rows the paging scenario then counted.
 */
private fun String.withDatabase(name: String): String {
    val query = substringAfter("?", "").let { if (it.isEmpty()) "" else "?$it" }
    val base = substringBefore("?").trimEnd('/')
    // "mongodb://host:port" has two slashes; a third one starts the database path.
    val host = if (base.count { it == '/' } > 2) base.substringBeforeLast('/') else base
    return "$host/$name$query"
}
