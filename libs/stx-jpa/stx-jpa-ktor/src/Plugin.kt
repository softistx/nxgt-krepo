package com.softistx.jpa.ktor

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaConfig
import com.softistx.ktor.resource
import io.ktor.server.application.*
import io.ktor.util.*
import jakarta.persistence.AttributeConverter
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

/**
 * One session factory for the application, closed when it stops.
 *
 * ```kotlin
 * install(JpaConnection) {
 *     config = JpaConfig(uri = System.getenv("POSTGRES_URI"), username = …, password = …)
 *     packages("com.acme.orders.domain") // or entities(Order::class, Customer::class)
 * }
 *
 * get("/orders/{id}") {
 *     call.respond(call.jpa.transaction { it.get<Order>(call.parameters["id"]!!.toLong()) })
 * }
 * ```
 *
 * A factory is expensive to build and cheap to share, and its sessions are neither shared nor
 * long-lived: a route opens one for the length of a `transaction { }` block and it goes away with
 * the block. That is why this owns the factory and nothing else.
 *
 * **The bootstrap is blocking here, and on purpose.** `Jpa.connect` suspends and plugin installation
 * does not, so this is where `runBlocking` is called — at startup, on the thread starting the
 * application, before anything is serving. The alternative is a server that accepts requests while
 * its mapping metadata is still being built.
 *
 * **Nothing connects at install.** The pool opens its first connection when a route asks for a
 * session, so a wrong password is a failed request rather than a failed startup. `SchemaMode.VALIDATE`
 * is the cheap way to turn that back into a startup failure, when the schema is managed elsewhere.
 */
val JpaConnection =
    createApplicationPlugin(name = "Jpa", createConfiguration = ::JpaConnectionConfiguration) {
        application.resource(JpaKey, pluginConfig.instance) {
            runBlocking {
                with(pluginConfig) {
                    if (packages.isEmpty()) {
                        Jpa.connect(config, entities, converters)
                    } else {
                        Jpa.scan(config, packages, entities, converters)
                    }
                }
            }
        }
        if (pluginConfig.injectable) application.provideJpa()
    }

/** What [JpaConnection] connects with, and what it maps. */
class JpaConnectionConfiguration {
    /** The URI, the credentials, the schema, and what Hibernate may do to it at startup. */
    var config: JpaConfig = JpaConfig()

    /**
     * The entity classes this factory knows about.
     *
     * An entity that is missing here — and not found by [packages] either — is not a mapping error at
     * startup: it is an `IllegalArgumentException` on the first query that names it.
     */
    var entities: List<KClass<*>> = emptyList()

    /**
     * Packages to read entities and converters off the classpath instead of naming them.
     *
     * ```kotlin
     * install(JpaConnection) {
     *     config = JpaConfig(uri = System.getenv("POSTGRES_URI"), username = …, password = …)
     *     packages("com.acme.orders.domain")
     * }
     * ```
     *
     * Whatever a scan finds is added to [entities] and [converters], for the class that lives
     * somewhere it does not reach. A scan that finds no entity fails the install rather than starting
     * a server that maps nothing.
     */
    var packages: List<String> = emptyList()

    /** Application converters, on top of the ones `stx-jpa` registers for `Instant` and `Uuid`. */
    var converters: List<KClass<out AttributeConverter<*, *>>> = emptyList()

    /** The readable spelling: `entities(Order::class, Customer::class)`. */
    fun entities(vararg classes: KClass<*>) {
        entities = classes.toList()
    }

    /** The readable spelling: `packages("com.acme.orders.domain")`. */
    fun packages(vararg names: String) {
        packages = names.toList()
    }

    /** The same for converters. */
    fun converters(vararg classes: KClass<out AttributeConverter<*, *>>) {
        converters = classes.toList()
    }

    /**
     * A factory built elsewhere — by a DI container, or by hand around a Vert.x the application
     * already runs.
     *
     * When set, [config], [entities] and [packages] are ignored and this is **not** closed when the application
     * stops: whoever created it closes it.
     */
    var instance: Jpa? = null

    /**
     * Registers the factory with Ktor's DI as well, so a class the container builds can take a [Jpa]
     * in its constructor — the same one `call.jpa` hands a route.
     *
     * Off by default, and it has to be: `ktor-server-di` is compile-only in this module, so an
     * application that never asks for this must not be made to carry it at runtime. Setting it calls
     * [provideJpa], which lives in its own file for that reason.
     *
     * The container closes what it hands out when the application stops, so this hands it a second
     * claim on closing the factory. That is safe — `Jpa.close` goes through `CloseGuard` — but a
     * factory that has to outlive the application does not belong in it.
     */
    var injectable: Boolean = true
}

internal val JpaKey = AttributeKey<Jpa>("com.softistx.jpa.Jpa")
