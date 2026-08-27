package com.strange.jpa

import io.vertx.core.Vertx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.hibernate.cfg.Configuration
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder
import org.hibernate.reactive.stage.Stage
import org.hibernate.reactive.vertx.VertxInstance
import kotlin.reflect.KClass

/**
 * A session factory built by hand, for the specs that ask what Hibernate Reactive does before this
 * module has decided what to do about it.
 *
 * This is the bootstrap `Jpa.connect` will be, written here first on purpose: the threading rule it
 * has to be designed around is a fact about the library, and the spec that establishes it cannot
 * depend on the design it is meant to justify.
 */
internal suspend fun testFactory(
    schema: String,
    vararg entities: KClass<*>,
    vertx: Vertx = Vertx.vertx(),
): Pair<Stage.SessionFactory, Vertx> =
    withContext(Dispatchers.IO) {
        val configuration =
            Configuration().apply {
                setProperty("hibernate.connection.url", JpaTestDatabase.endpoint.uri)
                setProperty("hibernate.connection.username", JpaTestDatabase.endpoint.username)
                setProperty("hibernate.connection.password", JpaTestDatabase.endpoint.password)
                setProperty("hibernate.default_schema", schema)
                setProperty("hibernate.hbm2ddl.auto", "create-drop")
                entities.forEach { addAnnotatedClass(it.java) }
            }

        val registry =
            ReactiveServiceRegistryBuilder()
                .applySettings(configuration.properties)
                .addService(VertxInstance::class.java, VertxInstance { vertx })
                .build()

        configuration.buildSessionFactory(registry).unwrap(Stage.SessionFactory::class.java) to vertx
    }

/** Closes both, in the order that does not strand the other. */
internal suspend fun Pair<Stage.SessionFactory, Vertx>.shutdown() {
    withContext(Dispatchers.IO) { first.close() }
    second.close().toCompletionStage().await()
}
