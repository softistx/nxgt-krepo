package com.strange.jpa.criteria

import com.strange.jpa.Jpa
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.createQuery
import com.strange.jpa.session.createUpdate
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * The operators, functions and paths — extensions on Criteria's own types, and nothing else.
 *
 * What each one is for is in its KDoc; what this pins is that they compose into a real statement
 * against a real database, and that the builder can be taken back off any node.
 */
class VocabularyTest :
    FeatureSpec({

        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "Ada", "gold")
            val bo = Buyer(2, "bo", null)
            persist(ada, bo)
            persist(
                Purchase(1, "P-1", 150, ada),
                Purchase(2, "P-2", 50, bo),
                Purchase(3, "P-3", 400, ada),
            )
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { it.seed() }
                block(jpa)
            }

        /** Every scenario below asks the same question: which references match this restriction? */
        suspend fun Jpa.matching(restrict: (Root<Purchase>) -> Predicate): List<String> =
            session { session ->
                val criteria = session.createQuery<Purchase>()
                val purchase = criteria.from(Purchase::class.java)
                criteria.where(restrict(purchase))
                criteria.orderBy(asc(purchase[Purchase::id]))

                session.query(criteria).list()
            }.map { it.reference }

        feature("the comparisons").config(enabled = JpaTestDatabase.available) {
            scenario("compare against a value, and against another expression") {
                seeded { jpa ->
                    jpa.matching { it[Purchase::total] gt 100L } shouldContainExactly listOf("P-1", "P-3")
                    jpa.matching { it[Purchase::total] le 150L } shouldContainExactly listOf("P-1", "P-2")
                    jpa.matching { it[Purchase::total] within 50L..150L } shouldContainExactly listOf("P-1", "P-2")
                    jpa.matching { it[Purchase::reference] eq "P-2" } shouldContainExactly listOf("P-2")
                    jpa.matching { it[Purchase::id] ne 1L } shouldContainExactly listOf("P-2", "P-3")
                }
            }

            scenario("match a set, a pattern, and a pattern that ignores case") {
                seeded { jpa ->
                    jpa.matching { it[Purchase::reference] oneOf listOf("P-1", "P-9") } shouldContainExactly listOf("P-1")
                    jpa.matching { it[Purchase::reference] oneOf emptyList() }.shouldContainExactly(emptyList())
                    jpa.matching {
                        it[Purchase::customer][Buyer::name] like "Ad%"
                    } shouldContainExactly listOf("P-1", "P-3")
                    jpa.matching {
                        it[Purchase::customer][Buyer::name] ilike "ad%"
                    } shouldContainExactly listOf("P-1", "P-3")
                }
            }

            scenario("combine, and fold a list of them down to one") {
                seeded { jpa ->
                    jpa.matching {
                        (it[Purchase::total] gt 100L) and (it[Purchase::reference] eq "P-3")
                    } shouldContainExactly listOf("P-3")

                    jpa.matching {
                        (it[Purchase::reference] eq "P-1") or (it[Purchase::reference] eq "P-2")
                    } shouldContainExactly listOf("P-1", "P-2")

                    jpa.matching {
                        all(listOf(it[Purchase::total] gt 100L, it[Purchase::total] lt 400L))!!
                    } shouldContainExactly listOf("P-1")

                    all(emptyList()) shouldBe null
                    any(emptyList()) shouldBe null
                }
            }
        }

        feature("the functions").config(enabled = JpaTestDatabase.available) {
            scenario("apply to a joined column, nesting the way they read") {
                seeded { jpa ->
                    jpa.matching {
                        lower(it[Purchase::customer][Buyer::name]) eq "ada"
                    } shouldContainExactly listOf("P-1", "P-3")

                    jpa.matching {
                        length(trim(it[Purchase::customer][Buyer::name])) eq 2
                    } shouldContainExactly listOf("P-2")
                }
            }

            scenario("reach a database function this package has not named") {
                seeded { jpa ->
                    jpa.matching { purchase ->
                        val builder = purchase.builder
                        builder.function<String>("upper", purchase[Purchase::reference]) eq "P-2"
                    } shouldContainExactly listOf("P-2")
                }
            }

            scenario("drop a fragment of SQL in, with its arguments bound") {
                seeded { jpa ->
                    jpa.matching { purchase ->
                        val builder = purchase.builder
                        // `~` binds tighter than the `=` that `eq true` adds; a fragment ending in
                        // a comparison of its own would need that comparison inside the fragment.
                        builder.sql<Boolean>("? ~ ?", purchase[Purchase::reference], builder.literal("^P-3")) eq true
                    } shouldContainExactly listOf("P-3")
                }
            }

            scenario("and a fragment whose placeholders do not match its arguments is refused here") {
                seeded { jpa ->
                    jpa.session { session ->
                        val criteria = session.createQuery<Purchase>()
                        val purchase = criteria.from(Purchase::class.java)

                        shouldThrow<IllegalStateException> {
                            purchase.builder.sql<Boolean>("? > ?", purchase[Purchase::total])
                        }.message!! shouldContain "2 placeholders and 1 arguments"
                    }
                }
            }
        }

        feature("the arithmetic").config(enabled = JpaTestDatabase.available) {
            scenario("assigns a column from itself, in one statement") {
                seeded { jpa ->
                    jpa.transaction { session ->
                        val statement = session.createUpdate<Purchase>()
                        val purchase = statement.from(Purchase::class.java)
                        // The type argument picks JPA's `set(Path<Y>, Expression<out Y>)`; without
                        // it the value-taking overload is an equally good candidate and neither wins.
                        statement.set<Long>(purchase[Purchase::total], purchase[Purchase::total] + 1L)
                        statement.where(purchase[Purchase::reference] eq "P-1")

                        session.mutate(statement).execute()
                    } shouldBe 1

                    jpa.session { session ->
                        session.query<Long>("select total from Purchase where reference = 'P-1'").single()
                    } shouldBe 151L
                }
            }
        }

        feature("the builder, taken off a node").config(enabled = JpaTestDatabase.available) {
            scenario("is the same object the session hands out") {
                seeded { jpa ->
                    jpa.session { session ->
                        val criteria = session.createQuery<Purchase>()
                        val purchase = criteria.from(Purchase::class.java)

                        purchase.builder shouldBe session.criteria
                        purchase[Purchase::total].builder shouldBe session.criteria
                    }
                }
            }
        }
    })
