package com.softistx.jpa

import com.softistx.jpa.entity.Bin
import com.softistx.jpa.entity.BinItem
import com.softistx.jpa.entity.Pallet
import com.softistx.jpa.entity.PalletItem
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Cascade owned by the schema rather than by the persistence context.
 *
 * This is the shape a repository with `stx-migrations` should prefer: the DDL is hand-written there
 * anyway, `SchemaMode` is `NONE` in anything real, and a constraint deletes the children in the one
 * statement that deletes the parent — with no collection loaded and no per-child round trip.
 *
 * What it costs is that Hibernate has to be *told*, and the scenarios below are what happens when it
 * is not.
 */
class DatabaseCascadeTest :
    FeatureSpec({

        feature("@OnDelete(CASCADE)").config(enabled = JpaTestDatabase.available) {
            scenario("deletes the children in the parent's own statement, with nothing loaded") {
                JpaTestDatabase.withJpa(Pallet::class, PalletItem::class) { jpa ->
                    jpa.transaction { session ->
                        val pallet = Pallet(1)
                        session.persist(pallet)
                        session.persist(PalletItem(1, "mug", pallet), PalletItem(2, "cup", pallet))
                    }

                    // No fetch, no collection, no orphanRemoval. The constraint does the work.
                    jpa.transaction { session -> session.remove(session.get<Pallet>(1L)) }

                    JpaTestDatabase.rows(jpa.config.schema!!, "pallet_items") shouldBe 0
                }
            }
        }

        feature("the same schema with Hibernate not told").config(enabled = JpaTestDatabase.available) {
            scenario("removing the parent fails, because Hibernate tries to orphan the children first") {
                // A plain `@OneToMany(mappedBy = …)` means Hibernate owns the dissociation: on remove
                // it nulls each child's foreign key before deleting the parent. Against a column that
                // refuses null that is a constraint violation, and it is the trap between the two
                // cascade models — the schema would have handled it, if anything had asked it to.
                JpaTestDatabase.withJpa(Bin::class, BinItem::class) { jpa ->
                    jpa.transaction { session ->
                        val bin = Bin(1)
                        session.persist(bin)
                        session.persist(BinItem(1, "mug", bin))
                    }

                    shouldThrowAny {
                        jpa.transaction { session -> session.remove(session.get<Bin>(1L)) }
                    }
                }
            }

            scenario("and the row is still there afterwards, so nothing was half-done") {
                JpaTestDatabase.withJpa(Bin::class, BinItem::class) { jpa ->
                    jpa.transaction { session ->
                        val bin = Bin(1)
                        session.persist(bin)
                        session.persist(BinItem(1, "mug", bin))
                    }

                    shouldThrowAny { jpa.transaction { session -> session.remove(session.get<Bin>(1L)) } }

                    // Both rows survive: the constraint refused the update, and the transaction took
                    // the parent's delete down with it. A half-cascade is the failure this rules out.
                    jpa.session { session -> session.find<Bin>(1L) } shouldNotBe null
                    JpaTestDatabase.rows(jpa.config.schema!!, "bin_items") shouldBe 1
                }
            }
        }
    })
