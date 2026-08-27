package com.strange.jpa.session

import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.Thing
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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

        feature("a transaction").config(enabled = JpaTestDatabase.available) {
            scenario("commits what it wrote, and hands back what the block returned") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    val written =
                        jpa.transaction {
                            it.persist(Thing(1, "committed")).await()
                            "done"
                        }

                    written shouldBe "done"
                    jpa.session { it.find(Thing::class.java, 1L).await() }.name shouldBe "committed"
                }
            }

            scenario("rolls back when the block throws, and the throw reaches the caller") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    shouldThrow<IllegalStateException> {
                        jpa.transaction {
                            it.persist(Thing(2, "rolled back")).await()
                            error("no")
                        }
                    }

                    jpa.session { it.find(Thing::class.java, 2L).await() }.shouldBeNull()
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
                    jpa.statelessTransaction { it.insert(Thing(3, "bulk")).await() }

                    jpa.statelessSession { it.get(Thing::class.java, 3L).await() }.name shouldBe "bulk"
                }
            }
        }
    })
