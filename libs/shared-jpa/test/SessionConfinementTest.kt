package com.strange.jpa

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.vertx.core.Vertx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.concurrent.Executor

/**
 * Which thread a coroutine is on when it comes back to the session — the question this library is
 * designed around, asked before the design exists.
 *
 * Hibernate Reactive confines a session to the Vert.x context that opened it: *"You're only allowed
 * to use the session from the thread that owns this local context"*. A coroutine that suspends
 * inside a session block resumes wherever its dispatcher puts it, which is not that thread.
 *
 * The failure is **`HR000069`**, and not the `HR000068` the reference documentation quotes — that
 * one is for a call arriving from no Vert.x thread at all. 69 is the case a coroutine actually
 * produces, and it names both threads:
 *
 * ```
 * HR000069: Detected use of the reactive Session from a different Thread than the one which was
 * used to open the reactive Session - this suggests an invalid integration;
 * original thread [181]: 'vert.x-eventloop-thread-1' current Thread [177]: 'DefaultDispatcher-worker-1'
 * ```
 *
 * Both halves are here on purpose. The naive bridge has to be shown to fail, or the confined one is
 * ceremony nobody can justify later.
 */
class SessionConfinementTest :
    FeatureSpec({

        feature("a coroutine that suspends inside a session").config(enabled = JpaTestDatabase.available) {
            scenario("loses the session's thread when it resumes on an ordinary dispatcher") {
                JpaTestDatabase.withSchema { schema ->
                    val factory = testFactory(schema, Thing::class)
                    try {
                        val failure =
                            runCatching {
                                factory.first
                                    .withTransaction { session ->
                                        CoroutineScope(Dispatchers.Default).future {
                                            // A real suspension. Everything after it is on a
                                            // Dispatchers.Default thread, and the session is not.
                                            delay(1)
                                            session.persist(Thing(1, "naive")).await()
                                        }
                                    }.toCompletableFuture()
                                    .await()
                            }.exceptionOrNull()

                        // If this ever stops throwing, the confined dispatcher below is unnecessary
                        // and this library got simpler — which is worth finding out from a failure
                        // here rather than from a production incident.
                        val message = "${failure?.cause?.message ?: failure?.message}"

                        message shouldContain "HR000069"
                        message shouldContain "vert.x-eventloop-thread"
                    } finally {
                        factory.shutdown()
                    }
                }
            }

            scenario("keeps it when every resumption is posted back to the Vert.x context") {
                JpaTestDatabase.withSchema { schema ->
                    val factory = testFactory(schema, Thing::class)
                    try {
                        val threads =
                            factory.first
                                .withTransaction { session ->
                                    val context = Vertx.currentContext()
                                    val confined =
                                        Executor { task -> context.runOnContext { task.run() } }
                                            .asCoroutineDispatcher()

                                    CoroutineScope(confined).future {
                                        val before = Thread.currentThread().name
                                        delay(1)
                                        session.persist(Thing(2, "confined")).await()
                                        before to Thread.currentThread().name
                                    }
                                }.toCompletableFuture()
                                .await()

                        threads.first shouldBe threads.second

                        val stored =
                            factory.first
                                .withTransaction { session ->
                                    val context = Vertx.currentContext()
                                    val confined =
                                        Executor { task -> context.runOnContext { task.run() } }
                                            .asCoroutineDispatcher()
                                    CoroutineScope(confined).future {
                                        delay(1)
                                        session.find(Thing::class.java, 2L).await()
                                    }
                                }.toCompletableFuture()
                                .await()

                        stored.name shouldBe "confined"
                    } finally {
                        factory.shutdown()
                    }
                }
            }
        }
    })
