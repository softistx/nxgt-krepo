package com.strange.jpa.scan

import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.scan.Auditable
import com.strange.jpa.entity.scan.Invoice
import com.strange.jpa.entity.scan.Money
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * What registering one entity actually maps — asked, because it decides what a scan has to collect.
 */
class MappedClassesTest :
    FeatureSpec({

        feature("an entity registered on its own").config(enabled = JpaTestDatabase.available) {
            scenario("maps its mapped superclass and its embeddable with it") {
                JpaTestDatabase.withJpa(Invoice::class) { jpa ->
                    // Neither Auditable nor Money is named anywhere, and both are mapped: Hibernate
                    // walks the superclass chain and the field types of what it is given. That is
                    // why `scanEntities` collects them for the split-package case rather than
                    // because anything here needs it.
                    jpa.transaction {
                        it.persist(Invoice(1, Money(500, "EUR")).apply { createdBy = "ada" })
                    }

                    val stored = jpa.session { it.get<Invoice>(1L) }
                    stored.createdBy shouldBe "ada"
                    stored.total.amount shouldBe 500L
                    stored.total.currency shouldBe "EUR"
                }
            }

            scenario("and its columns are named the way SQL is written, through the mapped superclass too") {
                JpaTestDatabase.withJpa(Invoice::class) { jpa ->
                    // `createdBy` is declared on the `@MappedSuperclass`, not on the entity, which is
                    // the half worth pinning here: the naming strategy is applied to what the mapping
                    // model ends up holding rather than to one class's own properties. `NamingTest`
                    // owns the rule itself.
                    JpaTestDatabase.columns(jpa.config.schema!!, "invoices") shouldContainExactly
                        listOf("amount", "created_by", "currency", "id")
                }
            }
        }
    })
