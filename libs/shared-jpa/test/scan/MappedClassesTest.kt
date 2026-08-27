package com.strange.jpa.scan

import com.strange.jpa.JpaTestDatabase
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

            scenario("and names the columns as the properties are written, not in snake case") {
                JpaTestDatabase.withJpa(Invoice::class) { jpa ->
                    // Worth pinning because it surprises everyone who has met this through Spring,
                    // which installs a camel-case-to-underscores strategy of its own. Hibernate on
                    // its own keeps the property name, and Postgres folds the unquoted identifier to
                    // lower case — so `createdBy` is the column `createdby`, and not `created_by`.
                    JpaTestDatabase.columns(jpa.config.schema!!, "invoices") shouldContainExactly
                        listOf("amount", "createdby", "currency", "id")
                }
            }
        }
    })
