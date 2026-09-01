package com.softistx.jpa

import com.softistx.jpa.entity.Thing
import com.softistx.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future

/**
 * Which thread a coroutine is on when it comes back to the session — the question this library is
 * designed around, and the answer it is built on.
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
 * Both halves are here on purpose. The naive bridge has to be shown to fail, or `confined` is
 * ceremony nobody can justify to whoever touches this next.
 */
class SessionConfinementTest :
    FeatureSpec({

        feature("a coroutine that suspends inside a session").config(enabled = JpaTestDatabase.available) {
            scenario("loses the session's thread when it resumes on an ordinary dispatcher") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    val failure =
                        runCatching {
                            jpa.factory
                                .withTransaction { session ->
                                    CoroutineScope(Dispatchers.Default).future {
                                        // A real suspension. Everything after it runs on a
                                        // Dispatchers.Default thread, and the session is not there.
                                        delay(1)
                                        session.persist(Thing(1, "naive"))
                                    }
                                }.toCompletableFuture()
                                .await()
                        }.exceptionOrNull()

                    // If this ever stops throwing, `confined` is unnecessary and this library got
                    // simpler — worth finding out from a failure here rather than from an incident.
                    val message = "${failure?.cause?.message ?: failure?.message}"

                    message shouldContain "HR000069"
                    message shouldContain "vert.x-eventloop-thread"
                }
            }

            scenario("keeps it when the transaction is this module's, and the row is there afterwards") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    val threads =
                        jpa.transaction { session ->
                            val before = Thread.currentThread().name
                            delay(1)
                            session.persist(Thing(2, "confined"))
                            before to Thread.currentThread().name
                        }

                    threads.first shouldContain "vert.x-eventloop-thread"
                    threads.second shouldBe threads.first

                    jpa
                        .transaction { session ->
                            delay(1)
                            session.get<Thing>(2L)
                        }.name shouldBe "confined"
                }
            }
        }
    })
