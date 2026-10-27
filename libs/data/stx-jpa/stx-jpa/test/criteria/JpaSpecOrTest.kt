package com.softistx.jpa.criteria

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaTestDatabase
import com.softistx.jpa.entity.Buyer
import com.softistx.jpa.entity.Purchase
import com.softistx.jpa.entity.PurchaseLine
import com.softistx.jpa.query.findAll
import com.softistx.jpa.session.createQuery
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import jakarta.persistence.criteria.Root

/**
 * `or` drops a side that restricts nothing, so `null or b` is `b` alone, never every row — the same
 * rule as `and`. This pins it, in the composition and against the rows it selects.
 */
class JpaSpecOrTest :
    FeatureSpec({

        val nothing: JpaSpec<Purchase> = { null }
        val p1: JpaSpec<Purchase> = { it[Purchase::reference] eq "P-1" }
        val big: JpaSpec<Purchase> = { it[Purchase::total] gt 100L }

        /** Whether the composed spec restricts anything, judged on a root from a real query. */
        suspend fun Jpa.answers(spec: JpaSpec<Purchase>): Boolean? =
            session { session ->
                val criteria = session.createQuery<Purchase>()
                val root: Root<Purchase> = criteria.from(Purchase::class.java)
                spec(root) != null
            }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction {
                    val ada = Buyer(1, "Ada", "gold")
                    it.persist(ada)
                    it.persist(Purchase(1, "P-1", 150, ada), Purchase(2, "P-2", 50, ada), Purchase(3, "P-3", 400, ada))
                }
                block(jpa)
            }

        suspend fun Jpa.refs(spec: JpaSpec<Purchase>?): List<String> =
            session { it.findAll<Purchase>(spec) }.map { it.reference }.sorted()

        feature("or with a side that restricts nothing").config(enabled = JpaTestDatabase.available) {
            scenario("keeps the other side alone, and two empty sides stay empty") {
                seeded { jpa ->
                    jpa.answers(nothing or p1) shouldBe true
                    jpa.answers(p1 or nothing) shouldBe true
                    jpa.answers(nothing or nothing) shouldBe false
                    jpa.answers(p1 or big) shouldBe true
                }
            }

            scenario("so an absent optional filter narrows to the other side's rows") {
                seeded { jpa ->
                    jpa.refs(nothing or big) shouldContainExactly listOf("P-1", "P-3")
                    jpa.refs(big or nothing) shouldContainExactly listOf("P-1", "P-3")
                    jpa.refs(p1 or big) shouldContainExactly listOf("P-1", "P-3")
                    jpa.refs(nothing or nothing) shouldContainExactly listOf("P-1", "P-2", "P-3")
                    jpa.refs(null).size shouldBe 3
                }
            }
        }
    })
