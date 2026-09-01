package com.softistx.jpa.session

import com.softistx.jpa.JpaTestDatabase
import com.softistx.jpa.convert.InstantConverter
import com.softistx.jpa.convert.UuidConverter
import com.softistx.jpa.convert.kotlinConverters
import com.softistx.jpa.entity.Thing
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.LockModeType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** What each of the four entry points promises, against a real database. */
class SessionsTest :
    FeatureSpec({

        feature("a session without a transaction").config(enabled = JpaTestDatabase.available) {
            scenario("discards a write, because nothing flushes it") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.session { it.persist(Thing(1, "never written")) }

                    // No error, no warning, no row. A session flushes at the end of a unit of work
                    // only when there is a transaction — which is why `session` is for reads.
                    jpa.session { it.find<Thing>(1L) }.shouldBeNull()
                }
            }

            scenario("unless the block flushes it itself") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.session {
                        it.persist(Thing(2, "flushed"))
                        it.flush()
                    }

                    jpa.session { it.find<Thing>(2L) }?.name shouldBe "flushed"
                }
            }
        }

        feature("a transaction").config(enabled = JpaTestDatabase.available) {
            scenario("commits what it wrote, and hands back what the block returned") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    val written =
                        jpa.transaction {
                            it.persist(Thing(1, "committed"))
                            "done"
                        }

                    written shouldBe "done"
                    jpa.session { it.get<Thing>(1L) }.name shouldBe "committed"
                }
            }

            scenario("rolls back when the block throws, and the throw reaches the caller") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    shouldThrow<IllegalStateException> {
                        jpa.transaction {
                            it.persist(Thing(2, "rolled back"))
                            error("no")
                        }
                    }

                    jpa.session { it.find<Thing>(2L) }.shouldBeNull()
                }
            }

            scenario("is cancelled with the coroutine that started it") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    val entered = CompletableDeferred<Unit>()
                    val scope = CoroutineScope(Dispatchers.Default)

                    // The caller's job is kept in the confined context precisely so this works: a
                    // request that goes away must not leave a transaction running for a minute.
                    val work =
                        scope.launch {
                            jpa.transaction {
                                entered.complete(Unit)
                                delay(60.seconds)
                            }
                        }

                    entered.await()
                    work.cancel()

                    withTimeoutOrNull(10.seconds) { work.join() } shouldBe Unit
                    work.isCancelled shouldBe true
                }
            }
        }

        feature("a stateless transaction").config(enabled = JpaTestDatabase.available) {
            scenario("inserts without a persistence context behind it") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.statelessTransaction { it.insert(Thing(3, "bulk")) }

                    jpa.statelessSession { it.get<Thing>(3L) }.name shouldBe "bulk"
                }
            }

            scenario("upserts, letting the database decide between an insert and an update") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    // The same call, once where the row is not there and once where it is.
                    jpa.statelessTransaction { it.upsert(Thing(4, "first")) }
                    jpa.statelessSession { it.get<Thing>(4L) }.name shouldBe "first"

                    jpa.statelessTransaction { it.upsert(Thing(4, "second")) }
                    jpa.statelessSession { it.get<Thing>(4L) }.name shouldBe "second"
                }
            }
        }

        feature("a lock").config(enabled = JpaTestDatabase.available) {
            scenario("is taken on a row this session already holds, and the row stays readable") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.persist(Thing(5, "locked")) }

                    jpa.transaction { session ->
                        val thing = session.get<Thing>(5L)
                        session.lock(thing, LockModeType.PESSIMISTIC_WRITE)

                        // The lock is held until the transaction ends; the point of the call is that
                        // it reaches Hibernate at all, which a raw `lock` returning a CompletionStage
                        // would let a caller forget to await.
                        thing.name = "still writable"
                    }

                    jpa.session { it.get<Thing>(5L) }.name shouldBe "still writable"
                }
            }
        }

        feature("the converters this module registers") {
            scenario("are the two Kotlin types JPA has no basic type for, and nothing else") {
                // Adding one changes the mapping of every application that upgrades, so the list is
                // pinned rather than left to be discovered by a column type changing under someone.
                kotlinConverters shouldContainExactly listOf(InstantConverter::class, UuidConverter::class)
            }
        }
    })
