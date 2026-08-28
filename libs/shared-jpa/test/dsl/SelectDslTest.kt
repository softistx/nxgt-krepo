package com.strange.jpa.dsl

import com.strange.jpa.JpaNoResultException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.query.criteria
import com.strange.jpa.query.query
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.persistence.criteria.JoinType

/** The typed query DSL: that it selects what HQL would, and says so when it does not. */
class SelectDslTest :
    FeatureSpec({

        // ada buys twice, bo once, and one purchase belongs to nobody.
        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "ada", "gold")
            val bo = Buyer(2, "bo", null)
            persist(ada, bo)
            val first = Purchase(1, "P-1", 150, ada)
            val second = Purchase(2, "P-2", 50, ada)
            val third = Purchase(3, "P-3", 400, bo)
            val orphan = Purchase(4, "P-4", 999, null)
            persist(first, second, third, orphan)
            persist(
                PurchaseLine(1, "apples", first),
                PurchaseLine(2, "pears", first),
                PurchaseLine(3, "apples", third),
            )
        }

        suspend fun <T> seeded(block: suspend (com.strange.jpa.Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { it.seed() }
                block(jpa)
            }

        feature("a selection").config(enabled = JpaTestDatabase.available) {
            scenario("returns exactly what the equivalent HQL returns") {
                seeded { jpa ->
                    val (dsl, hql) =
                        jpa.session { session ->
                            session
                                .select<Purchase> {
                                    where { Purchase::total gt 100L }
                                    orderBy { asc(Purchase::reference) }
                                }.list() to
                                session
                                    .query<Purchase>("from Purchase where total > 100 order by reference")
                                    .list()
                        }

                    dsl.map { it.reference } shouldContainExactly listOf("P-1", "P-3", "P-4")
                    dsl.map { it.reference } shouldContainExactly hql.map { it.reference }
                }
            }

            scenario("ands every where block together, in one query") {
                seeded { jpa ->
                    val found =
                        jpa.session { session ->
                            session
                                .select<Purchase> {
                                    where { Purchase::total gt 100L }
                                    where { Purchase::total lt 500L }
                                }.list()
                        }

                    found.map { it.reference } shouldContainExactly listOf("P-1", "P-3")
                }
            }

            scenario("adds nothing for a where block that answers with null") {
                seeded { jpa ->
                    val tier: String? = null
                    val found =
                        jpa.session { session ->
                            session
                                .select<Purchase> {
                                    where { tier?.let { _ -> Purchase::total gt 1_000L } }
                                    orderBy { asc(Purchase::id) }
                                }.list()
                        }

                    found.map { it.reference } shouldContainExactly listOf("P-1", "P-2", "P-3", "P-4")
                }
            }

            scenario("sorts by the keys in the order they were added") {
                seeded { jpa ->
                    val found =
                        jpa.session { session ->
                            session
                                .select<Purchase> {
                                    orderBy { desc(Purchase::total) }
                                    orderBy { asc(Purchase::id) }
                                }.list()
                        }

                    found.map { it.reference } shouldContainExactly listOf("P-4", "P-3", "P-1", "P-2")
                }
            }
        }

        feature("a join").config(enabled = JpaTestDatabase.available) {
            scenario("is declared once and read from twice") {
                seeded { jpa ->
                    val found =
                        jpa.session { session ->
                            session
                                .select<Purchase> {
                                    val customer = join(Purchase::customer)
                                    where { customer[Buyer::name] eq "ada" }
                                    orderBy { asc(customer[Buyer::id]) }
                                    orderBy { asc(Purchase::reference) }
                                }.list()
                        }

                    found.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                }
            }

            scenario("asked for twice is one join, not two") {
                seeded { jpa ->
                    // No `val`, and the join named in two separate chained lambdas: if each call
                    // took its own join, the second would be a second row source and the two
                    // conditions could never both hold.
                    val found =
                        jpa.session { session ->
                            session
                                .select<Purchase>()
                                .where { join(Purchase::customer)[Buyer::name] eq "ada" }
                                .where { join(Purchase::customer)[Buyer::tier] eq "gold" }
                                .orderBy { asc(join(Purchase::customer)[Buyer::id]) }
                                .orderBy { asc(Purchase::reference) }
                                .list()
                        }

                    found.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                }
            }

            scenario("asked for twice as two different kinds is refused, rather than quietly inner") {
                seeded { jpa ->
                    val refused =
                        shouldThrow<IllegalStateException> {
                            jpa.session { session ->
                                session
                                    .select<Purchase>()
                                    .where { join(Purchase::customer)[Buyer::name] eq "ada" }
                                    .where { join(Purchase::customer, JoinType.LEFT)[Buyer::tier].isNull() }
                                    .list()
                            }
                        }

                    refused.message shouldContain "already joined as INNER and this asks for LEFT"
                }
            }

            scenario("inner drops the rows with nothing to join to, left keeps them") {
                seeded { jpa ->
                    val (inner, left) =
                        jpa.session { session ->
                            session
                                .select<Purchase> {
                                    join(Purchase::customer)
                                    orderBy { asc(Purchase::id) }
                                }.list() to
                                session
                                    .select<Purchase> {
                                        join(Purchase::customer, JoinType.LEFT)
                                        orderBy { asc(Purchase::id) }
                                    }.list()
                        }

                    inner.map { it.reference } shouldContainExactly listOf("P-1", "P-2", "P-3")
                    left.map { it.reference } shouldContainExactly listOf("P-1", "P-2", "P-3", "P-4")
                }
            }

            scenario("over a to-many returns the owner once per element, until distinct") {
                seeded { jpa ->
                    val (duplicated, collapsed) =
                        jpa.session { session ->
                            session
                                .select<Purchase> {
                                    val lines = joinEach(Purchase::lines)
                                    where { lines[PurchaseLine::sku] eq "apples" }
                                    orderBy { asc(Purchase::id) }
                                }.list() to
                                session
                                    .select<Purchase> {
                                        val lines = joinEach(Purchase::lines)
                                        where { lines[PurchaseLine::sku] like "%p%" }
                                        distinct()
                                        orderBy { asc(Purchase::id) }
                                    }.list()
                        }

                    duplicated.map { it.reference } shouldContainExactly listOf("P-1", "P-3")
                    collapsed.map { it.reference } shouldContainExactly listOf("P-1", "P-3")
                }
            }
        }

        feature("the chain and the block")
            .config(enabled = JpaTestDatabase.available) {
                scenario("say the same query, and may be mixed") {
                    seeded { jpa ->
                        val chained =
                            jpa.session { session ->
                                session
                                    .select<Purchase>()
                                    .where { Purchase::total gt 100L }
                                    .orderBy { asc(Purchase::reference) }
                                    .limit(2)
                                    .list()
                            }
                        val blocked =
                            jpa.session { session ->
                                session
                                    .select<Purchase> {
                                        where { Purchase::total gt 100L }
                                        orderBy { asc(Purchase::reference) }
                                        limit(2)
                                    }.list()
                            }
                        val mixed =
                            jpa.session { session ->
                                session
                                    .select<Purchase> { where { Purchase::total gt 100L } }
                                    .orderBy { asc(Purchase::reference) }
                                    .limit(2)
                                    .list()
                            }

                        chained.map { it.reference } shouldContainExactly listOf("P-1", "P-3")
                        blocked.map { it.reference } shouldContainExactly chained.map { it.reference }
                        mixed.map { it.reference } shouldContainExactly chained.map { it.reference }
                    }
                }
            }

        feature("the operators").config(enabled = JpaTestDatabase.available) {
            suspend fun references(block: SelectScope<Purchase>.() -> Unit): List<String> =
                seeded { jpa ->
                    jpa
                        .session { session ->
                            session
                                .select<Purchase> {
                                    block()
                                    orderBy { asc(Purchase::id) }
                                }.list()
                        }.map { it.reference }
                }

            scenario("compare, both ways round") {
                references { where { Purchase::total ge 400L } } shouldContainExactly listOf("P-3", "P-4")
                references { where { Purchase::total le 50L } } shouldContainExactly listOf("P-2")
                references { where { Purchase::total within 50L..150L } } shouldContainExactly listOf("P-1", "P-2")
                references { where { Purchase::reference ne "P-1" } } shouldContainExactly
                    listOf("P-2", "P-3", "P-4")
            }

            // Regression: the property form used to carry only the value overloads for gt/ge/lt/le,
            // so `this[Purchase::total] gt this[Purchase::id]` compiled and this did not.
            scenario("compare a property against another column, not only against a value") {
                references { where { Purchase::total gt this[Purchase::id] } }.size shouldBe 4
                references { where { Purchase::id ge this[Purchase::total] } }
                    .shouldContainExactly(emptyList())
                references { where { Purchase::total lt (this[Purchase::id] * 25L) } }
                    .shouldContainExactly(emptyList())
                references { where { Purchase::total le (this[Purchase::id] * 25L) } } shouldContainExactly
                    listOf("P-2")
            }

            scenario("match text, with and without case") {
                references { where { Purchase::reference like "P-_" } }.size shouldBe 4
                references { where { Purchase::reference ilike "p-1" } } shouldContainExactly listOf("P-1")
                references { where { Purchase::reference notLike "P-1" } } shouldContainExactly
                    listOf("P-2", "P-3", "P-4")
            }

            scenario("null is asked for with isNull, never with eq") {
                references { where { Purchase::customer.isNull() } } shouldContainExactly listOf("P-4")
                references { where { Purchase::customer.isNotNull() } } shouldContainExactly
                    listOf("P-1", "P-2", "P-3")
                references { where { Purchase::customer eq null } }.shouldContainExactly(emptyList())
            }

            scenario("in a list, including an empty one") {
                references { where { Purchase::reference oneOf listOf("P-1", "P-3") } } shouldContainExactly
                    listOf("P-1", "P-3")
                references { where { Purchase::reference oneOf emptyList() } }.shouldContainExactly(emptyList())
            }

            scenario("and, or and not, with the parentheses Kotlin needs") {
                references {
                    where { (Purchase::total gt 300L) or (Purchase::reference eq "P-2") }
                } shouldContainExactly listOf("P-2", "P-3", "P-4")
                references {
                    where { !(Purchase::total gt 100L) }
                } shouldContainExactly listOf("P-2")
                references {
                    where { all(listOf(Purchase::total gt 100L, Purchase::total lt 500L)) }
                } shouldContainExactly listOf("P-1", "P-3")
                references { where { all(emptyList()) } }.size shouldBe 4
            }
        }

        feature("what it says when it fails").config(enabled = JpaTestDatabase.available) {
            scenario("names the query in HQL, since there is no source text to quote") {
                seeded { jpa ->
                    val failure =
                        shouldThrow<JpaNoResultException> {
                            jpa.session { session ->
                                session
                                    .select<Purchase> { where { Purchase::reference eq "nothing" } }
                                    .single()
                            }
                        }

                    failure.message shouldContain "Purchase"
                }
            }
        }

        feature("the builder taken off a node").config(enabled = JpaTestDatabase.available) {
            scenario("is the session factory's own, which is what makes the operators extensions") {
                seeded { jpa ->
                    jpa.session { session ->
                        val criteria = session.raw.criteria.createQuery(Purchase::class.java)
                        val path = criteria.from(Purchase::class.java).get<Long>("total")

                        path.builder shouldBe jpa.factory.criteriaBuilder
                    }
                }
            }
        }
    })
