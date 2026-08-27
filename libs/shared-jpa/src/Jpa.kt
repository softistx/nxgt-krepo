package com.strange.jpa

import com.strange.common.lifecycle.CloseGuard
import com.strange.jpa.convert.kotlinConverters
import io.vertx.core.Vertx
import jakarta.persistence.AttributeConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hibernate.cfg.Configuration
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder
import org.hibernate.reactive.stage.Stage
import org.hibernate.reactive.vertx.VertxInstance
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * A Hibernate Reactive session factory, its Vert.x, and the entities it was told about.
 *
 * ```kotlin
 * val jpa = Jpa.connect(JpaConfig(uri = System.getenv("POSTGRES_URI"), username = …), Order::class)
 *
 * val order = jpa.transaction { session -> session.find(Order::class.java, id).await() }
 * ```
 *
 * **Configured in code rather than through `persistence.xml`.** Hibernate Reactive supports both;
 * this way the configuration is a value a test can vary, a container can build and an application
 * can assemble from its environment, instead of a file that has to exist on a classpath.
 *
 * **The Vert.x instance is ours unless one is handed in.** Hibernate would happily create its own,
 * and then nothing else could reach it — and reaching it is the whole point, because the coroutine
 * bridge in `com.strange.jpa.session` needs the context a session was opened on.
 */
class Jpa internal constructor(
    /** The factory itself, for everything this module has not wrapped. */
    val factory: Stage.SessionFactory,
    val config: JpaConfig,
    private val vertx: Vertx,
    private val ownsVertx: Boolean,
) : AutoCloseable {
    private val guard = CloseGuard()

    val isOpen: Boolean get() = factory.isOpen

    /**
     * Closes the factory, and the Vert.x behind it when this opened it. Calling it again does
     * nothing.
     *
     * It blocks, briefly and boundedly. `close()` cannot suspend, and a shutdown that returns
     * before the pool is actually closed leaves connections open on the server for as long as it
     * takes them to time out there. It is called from application shutdown and container teardown,
     * never from an event loop.
     */
    override fun close() =
        guard.once {
            runCatching { factory.close() }
            if (ownsVertx) {
                runCatching {
                    vertx
                        .close()
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(CLOSE_SECONDS, TimeUnit.SECONDS)
                }
            }
        }

    companion object {
        private const val CLOSE_SECONDS = 5L

        /**
         * Builds the factory for [entities], connecting as [config] says.
         *
         * Suspending because none of what it does is cheap or non-blocking: reading the annotations
         * off every entity, building the metadata model and standing up the service registry are
         * all ordinary blocking work, so they happen on [Dispatchers.IO] rather than on whatever
         * thread started the application.
         *
         * Nothing connects yet. The pool opens its first connection when something asks for a
         * session, so a wrong password surfaces on first use and not here.
         *
         * [converters] are added to this module's own — an entity's `kotlin.time.Instant` and
         * `kotlin.uuid.Uuid` attributes are mapped whether a caller passes anything or not. They
         * have to be named here because `addAnnotatedClass` does not find an `@Converter` the way a
         * classpath scan would; that is the cost of a programmatic bootstrap, and it is paid once.
         */
        suspend fun connect(
            config: JpaConfig,
            entities: List<KClass<*>>,
            converters: List<KClass<out AttributeConverter<*, *>>> = emptyList(),
            vertx: Vertx? = null,
        ): Jpa =
            withContext(Dispatchers.IO) {
                require(entities.isNotEmpty()) { "Jpa.connect needs at least one entity class" }
                rejectUuidIdentifiers(entities)

                val own = vertx == null
                val instance = vertx ?: Vertx.vertx()

                try {
                    val configuration =
                        Configuration().apply {
                            config.settings().forEach { (key, value) -> setProperty(key, value) }
                            entities.forEach { addAnnotatedClass(it.java) }
                            (kotlinConverters + converters).forEach { addAttributeConverter(it.java) }
                        }

                    val registry =
                        ReactiveServiceRegistryBuilder()
                            .applySettings(configuration.properties)
                            .addService(VertxInstance::class.java, VertxInstance { instance })
                            .build()

                    val factory = configuration.buildSessionFactory(registry).unwrap(Stage.SessionFactory::class.java)

                    Jpa(factory, config, instance, own)
                } catch (failure: Throwable) {
                    // A Vert.x this created and could not hand back would otherwise keep its event
                    // loops alive for the life of the JVM.
                    if (own) runCatching { instance.close() }
                    throw failure
                }
            }

        /** The same, spelled for the common case. */
        suspend fun connect(
            config: JpaConfig,
            vararg entities: KClass<*>,
        ): Jpa = connect(config, entities.toList())
    }
}
