package com.strange.jpa.dsl

import com.strange.jpa.Jpa
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * The entry points that take the entity as a value.
 *
 * `select`, `project`, `delete`, `find` and `get` are all `inline reified`, which a class generic in
 * its entity cannot reach: a type parameter is not reifiable, so `select<T>()` does not compile
 * inside one. Every scenario here goes through [Generic], which holds nothing but a `KClass` and a
 * `KProperty1` — the position `JpaRepository` is in.
 */
class GenericEntryPointsTest :
    FeatureSpec({

        /** A stand-in for a repository: generic in its entity, with no way to reify it. */
        class Generic<T : Any, ID : Any>(
            private val type: KClass<T>,
            private val id: KProperty1<T, ID>,
        ) {
            suspend fun findById(
                session: JpaSession,
                value: ID,
            ): T? = session.find(type, value)

            suspend fun requireById(
                session: JpaSession,
                value: ID,
            ): T = session.get(type, value)

            suspend fun all(session: JpaSession): List<T> = session.select(type).orderBy { asc(id) }.list()

            /**
             * The identifier column alone — which needs the identifier's *class*, and a property
             * reference does not carry one without `kotlin-reflect`. Hibernate's metamodel does.
             */
            @Suppress("UNCHECKED_CAST")
            suspend fun ids(session: JpaSession): List<ID> {
                val idType =
                    session.raw.factory.metamodel
                        .entity(type.java)
                        .idType.javaType.kotlin as KClass<ID>
                return session.project(type, idType) { this[id] }.orderBy { asc(id) }.list()
            }

            suspend fun deleteById(
                session: JpaSession,
                value: ID,
            ): Int = session.delete(type).where { this[id] eq value }.execute()
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { session ->
                    session.persist(
                        Purchase(1, "P-1", 150),
                        Purchase(2, "P-2", 50),
                        Purchase(3, "P-3", 400),
                    )
                }
                block(jpa)
            }

        val purchases = Generic(Purchase::class, Purchase::id)

        feature("an entity named by value").config(enabled = JpaTestDatabase.available) {
            scenario("selects what the reified form selects") {
                seeded { jpa ->
                    val (generic, reified) =
                        jpa.session { session ->
                            purchases.all(session).map { it.reference } to
                                session
                                    .select<Purchase>()
                                    .orderBy { asc(Purchase::id) }
                                    .list()
                                    .map { it.reference }
                        }

                    generic shouldContainExactly reified
                    generic shouldContainExactly listOf("P-1", "P-2", "P-3")
                }
            }

            scenario("finds by id, and answers null rather than throwing") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.findById(session, 1L)?.reference shouldBe "P-1"
                        purchases.findById(session, 99L) shouldBe null
                    }
                }
            }

            scenario("requires by id, naming the entity it could not find") {
                seeded { jpa ->
                    val failure =
                        shouldThrow<JpaNotFoundException> {
                            jpa.session { session -> purchases.requireById(session, 99L) }
                        }

                    failure.message shouldContain "Purchase"
                    failure.message shouldContain "99"
                }
            }

            scenario("projects a single column, with the result type a value too") {
                seeded { jpa ->
                    jpa.session { session -> purchases.ids(session) } shouldContainExactly listOf(1L, 2L, 3L)
                }
            }

            scenario("deletes in bulk") {
                seeded { jpa ->
                    jpa.transaction { session -> purchases.deleteById(session, 2L) } shouldBe 1
                    jpa.session { session -> purchases.all(session).map { it.reference } } shouldContainExactly
                        listOf("P-1", "P-3")
                }
            }
        }
    })
