package com.softistx.jpa

import com.softistx.jpa.entity.Counter
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * `@Version`, and what a lost update looks like from a coroutine.
 *
 * The annotation is the easy half. The half worth a spec is that the conflict is detected at flush,
 * inside the reactive session, and reaches the caller as a thrown exception rather than as a failed
 * future nobody looks at — a suspending API can swallow that distinction, and this is where it would
 * be swallowed.
 */
class OptimisticLockTest :
    FeatureSpec({

        feature("a versioned entity").config(enabled = JpaTestDatabase.available) {
            scenario("counts its own updates") {
                JpaTestDatabase.withJpa(Counter::class) { jpa ->
                    jpa.transaction { session -> session.persist(Counter(1, 0)) }
                    jpa.transaction { session -> session.get<Counter>(1L).total = 5 }

                    jpa.session { session ->
                        val counter = session.get<Counter>(1L)

                        counter.total shouldBe 5
                        counter.version shouldBe 1
                    }
                }
            }

            scenario("refuses a write built on a version the row has left behind") {
                JpaTestDatabase.withJpa(Counter::class) { jpa ->
                    jpa.transaction { session -> session.persist(Counter(1, 0)) }

                    // Detached, holding version 0.
                    val stale = jpa.session { session -> session.get<Counter>(1L) }

                    // Someone else moves the row to version 1.
                    jpa.transaction { session -> session.get<Counter>(1L).total = 5 }

                    stale.total = 99
                    shouldThrowAny { jpa.transaction { session -> session.merge(stale) } }

                    // And the winner's value is the one that survived.
                    jpa.session { session -> session.get<Counter>(1L).total } shouldBe 5
                }
            }
        }
    })
