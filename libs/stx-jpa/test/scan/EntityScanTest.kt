package com.softistx.jpa.scan

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaConfig
import com.softistx.jpa.JpaMappingException
import com.softistx.jpa.JpaTestDatabase
import com.softistx.jpa.SchemaMode
import com.softistx.jpa.convert.InstantConverter
import com.softistx.jpa.convert.UuidConverter
import com.softistx.jpa.entity.scan.Auditable
import com.softistx.jpa.entity.scan.Invoice
import com.softistx.jpa.entity.scan.Money
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
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
                scanEntities("com.softistx.jpa.entity.scan") shouldContainExactlyInAnyOrder
                    listOf(Invoice::class, Auditable::class, Money::class)
            }

            scenario("finds the converters too, which connect cannot be asked to discover") {
                scanConverters("com.softistx.jpa.convert") shouldContainExactlyInAnyOrder
                    listOf(InstantConverter::class, UuidConverter::class)
            }

            scenario("refuses a package with no entity in it, rather than mapping nothing") {
                // The failure this exists to prevent: a scan that finds nothing starts perfectly and
                // fails on the first query, a long way from the package name that was wrong.
                val failure = shouldThrow<JpaMappingException> { scanEntities("com.softistx.jpa.session") }

                failure.message shouldContain "com.softistx.jpa.session"
            }

            scenario("refuses to scan nothing at all") {
                shouldThrow<JpaMappingException> { scanEntities(emptyList()) }
            }

            scenario("is content to find no converter, which is an addition rather than the mapping") {
                scanConverters("com.softistx.jpa.entity.scan") shouldBe emptyList()
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
                            "com.softistx.jpa.entity.scan",
                        ).use { jpa ->
                            jpa.transaction { it.persist(Invoice(1, Money(99, "GBP"))) }

                            jpa.session { it.get<Invoice>(1L) }.total.currency shouldBe "GBP"
                            JpaTestDatabase.columns(schema, "invoices") shouldContain "amount"
                        }
                }
            }
        }
    })
