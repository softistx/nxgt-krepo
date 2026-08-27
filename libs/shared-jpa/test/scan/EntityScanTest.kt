package com.strange.jpa.scan

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.SchemaMode
import com.strange.jpa.convert.InstantConverter
import com.strange.jpa.convert.UuidConverter
import com.strange.jpa.entity.scan.Auditable
import com.strange.jpa.entity.scan.Invoice
import com.strange.jpa.entity.scan.Money
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Reading the classpath instead of naming the classes — and what it does when there is nothing there. */
class EntityScanTest :
    FeatureSpec({

        feature("scanning a package") {
            scenario("finds the entity, and the superclass and embeddable beside it") {
                scanEntities("com.strange.jpa.entity.scan") shouldContainExactlyInAnyOrder
                    listOf(Invoice::class, Auditable::class, Money::class)
            }

            scenario("finds the converters too, which connect cannot be asked to discover") {
                scanConverters("com.strange.jpa.convert") shouldContainExactlyInAnyOrder
                    listOf(InstantConverter::class, UuidConverter::class)
            }

            scenario("refuses a package with no entity in it, rather than mapping nothing") {
                // The failure this exists to prevent: a scan that finds nothing starts perfectly and
                // fails on the first query, a long way from the package name that was wrong.
                val failure = shouldThrow<IllegalStateException> { scanEntities("com.strange.jpa.session") }

                failure.message shouldContain "com.strange.jpa.session"
            }

            scenario("refuses to scan nothing at all") {
                shouldThrow<IllegalArgumentException> { scanEntities(emptyList()) }
            }

            scenario("is content to find no converter, which is an addition rather than the mapping") {
                scanConverters("com.strange.jpa.entity.scan") shouldBe emptyList()
            }
        }

        feature("a factory built from a scan").config(enabled = JpaTestDatabase.available) {
            scenario("maps what it found and stores through it") {
                JpaTestDatabase.withSchema { schema ->
                    Jpa
                        .scan(
                            JpaConfig(
                                uri = JpaTestDatabase.endpoint.uri,
                                username = JpaTestDatabase.endpoint.username,
                                password = JpaTestDatabase.endpoint.password,
                                schema = schema,
                                schemaMode = SchemaMode.CREATE_DROP,
                            ),
                            "com.strange.jpa.entity.scan",
                        ).use { jpa ->
                            jpa.transaction { it.persist(Invoice(1, Money(99, "GBP"))) }

                            jpa.session { it.get<Invoice>(1L) }.total.currency shouldBe "GBP"
                            JpaTestDatabase.columns(schema, "invoices") shouldContain "amount"
                        }
                }
            }
        }
    })
