package com.softistx.jpa

import com.softistx.jpa.entity.LedgerEntry
import com.softistx.jpa.entity.LedgerKey
import com.softistx.jpa.entity.Rectangle
import com.softistx.jpa.query.query
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * A key made of two columns, and a column the database computes.
 *
 * `find` and `get` take `Any` as the identifier here, which is what makes `@EmbeddedId` a real
 * question rather than a formality: nothing in the signature says a composite key is allowed, so
 * whether one reaches Hibernate intact is measured.
 */
class CompositeKeyTest :
    FeatureSpec({

        feature("@EmbeddedId").config(enabled = JpaTestDatabase.available) {
            scenario("is a primary key of both columns, and get takes the key object") {
                JpaTestDatabase.withJpa(LedgerEntry::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(
                            LedgerEntry(LedgerKey("orders", 1), "created"),
                            LedgerEntry(LedgerKey("orders", 2), "paid"),
                            LedgerEntry(LedgerKey("users", 1), "signed up"),
                        )
                    }

                    JpaTestDatabase.columns(jpa.config.schema!!, "ledger_entries") shouldContainExactlyInAnyOrder
                        listOf("stream", "sequence", "payload")

                    jpa.session { session ->
                        session.get<LedgerEntry>(LedgerKey("orders", 2)).payload
                    } shouldBe "paid"
                }
            }

            scenario("and an absent key is absent rather than a failure") {
                JpaTestDatabase.withJpa(LedgerEntry::class) { jpa ->
                    jpa.session { session -> session.find<LedgerEntry>(LedgerKey("orders", 99)) } shouldBe null
                }
            }

            scenario("its parts are queryable as a path, which is why it is embeddable") {
                JpaTestDatabase.withJpa(LedgerEntry::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(
                            LedgerEntry(LedgerKey("orders", 1), "created"),
                            LedgerEntry(LedgerKey("users", 1), "signed up"),
                        )
                    }

                    jpa.session { session ->
                        session
                            .query<LedgerEntry>("from LedgerEntry where key.stream = :stream")
                            .parameter("stream", "orders")
                            .list()
                            .map { it.payload }
                    } shouldBe listOf("created")
                }
            }
        }

        feature("@Formula").config(enabled = JpaTestDatabase.available) {
            scenario("is computed by the database and never written") {
                JpaTestDatabase.withJpa(Rectangle::class) { jpa ->
                    jpa.transaction { session -> session.persist(Rectangle(1, 3, 4)) }

                    jpa.session { session -> session.get<Rectangle>(1L).area } shouldBe 12

                    // Not a column: it is a fragment spliced into the select.
                    JpaTestDatabase.columns(jpa.config.schema!!, "rectangles") shouldContainExactlyInAnyOrder
                        listOf("id", "width", "height")
                }
            }
        }
    })
