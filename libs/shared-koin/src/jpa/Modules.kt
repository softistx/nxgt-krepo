package com.strange.koin.jpa

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import jakarta.persistence.AttributeConverter
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.reflect.KClass

/**
 * One session factory for the container to hand out, closed when the container stops.
 *
 * ```kotlin
 * startKoin { modules(jpaModule(JpaConfig(uri = System.getenv("POSTGRES_URI")), Order::class), appModule) }
 * ```
 *
 * [entities] is the mapping, unless [packages] is given — then the classpath is read for the entities
 * and converters those packages hold, and anything named here is added to what the scan found. A class
 * missing from both is an `IllegalArgumentException` on the first query that names it rather than an
 * error at startup, which is the argument for naming them.
 *
 * **`runBlocking`, for the reason `amqpModule` spells out.** `Jpa.connect` suspends — reading
 * annotations off every entity and building the metadata model is real work — and Koin's `single { }`
 * has no suspending form. It happens on the thread starting the application, which is why this is
 * worth resolving eagerly rather than on the first request that needs it.
 *
 * In an application that also serves HTTP, install the plugin over this factory rather than building
 * a second: `install(JpaConnection) { instance = get() }`.
 */
fun jpaModule(
    config: JpaConfig = JpaConfig(),
    entities: List<KClass<*>>,
    converters: List<KClass<out AttributeConverter<*, *>>> = emptyList(),
    packages: List<String> = emptyList(),
): Module =
    module {
        single {
            runBlocking {
                if (packages.isEmpty()) {
                    Jpa.connect(config, entities, converters)
                } else {
                    Jpa.scan(config, packages, entities, converters)
                }
            }
        } onClose { it?.close() }
    }

/** The scanning spelling: `jpaScanModule(config, "com.acme.orders.domain")`. */
fun jpaScanModule(
    config: JpaConfig = JpaConfig(),
    vararg packages: String,
): Module = jpaModule(config, entities = emptyList(), packages = packages.toList())

/** The same, spelled for the common case: `jpaModule(config, Order::class, Customer::class)`. */
fun jpaModule(
    config: JpaConfig = JpaConfig(),
    vararg entities: KClass<*>,
): Module = jpaModule(config, entities.toList())
