package com.softistx.jpa

import com.softistx.jpa.criteria.secondaryFetches
import com.softistx.jpa.entity.Dossier
import com.softistx.jpa.entity.Passport
import com.softistx.jpa.entity.SharedRow
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * `@SecondaryTable` and `@DynamicUpdate` — two statements about the SQL Hibernate *generates*.
 *
 * Neither can be checked by storing a value and reading it back: that measurement agrees with
 * whatever the generator did. So each has a witness outside the mapping — the catalog for the second
 * table, and a lost update for the dynamic one.
 */
class SpreadTest :
    FeatureSpec({

        feature("an entity over two tables").config(enabled = JpaTestDatabase.available) {
            scenario("exports both, and writes a row into each") {
                JpaTestDatabase.withJpa(Passport::class) { jpa ->
                    jpa.transaction { session -> session.persist(Passport(1, "Ada", "photo.png")) }

                    // Asked of the server, not of the session: one entity, two rows, joined on the
                    // identifier the secondary table names.
                    JpaTestDatabase.rows(jpa.config.schema!!, "passports") shouldBe 1
                    JpaTestDatabase.rows(jpa.config.schema!!, "passport_photos") shouldBe 1
                    JpaTestDatabase.columns(jpa.config.schema!!, "passport_photos") shouldBe
                        listOf("passport_id", "photo")

                    jpa.session { session -> session.get<Passport>(1L).photo } shouldBe "photo.png"
                }
            }

            scenario("costs the join on every read, whether the second table was wanted or not") {
                JpaTestDatabase.withJpa(Passport::class) { jpa ->
                    jpa.transaction { session -> session.persist(Passport(1, "Ada", "photo.png")) }

                    // There is no `LAZY` for a secondary table — the join is in the entity's own
                    // select — so reading only `holder` still paid for `photo`. The witness is that
                    // it arrives without a secondary fetch: nothing goes back for it, because it
                    // was never left behind.
                    jpa.secondaryFetches { db ->
                        db.session { session ->
                            val passport = session.get<Passport>(1L)

                            passport.holder shouldBe "Ada"
                            passport.photo shouldBe "photo.png"
                        }
                    } shouldBe 0
                }
            }
        }

        feature("an update naming every column").config(enabled = JpaTestDatabase.available) {
            scenario("puts back a column somebody else changed while it was loaded") {
                JpaTestDatabase.withJpa(SharedRow::class) { jpa ->
                    jpa.transaction { session -> session.persist(SharedRow(1, "draft", "nobody")) }

                    jpa.withSecondFactory(SharedRow::class) { other ->
                        jpa.transaction { session ->
                            session.get<SharedRow>(1L).reviewer = "ada"
                            other.transaction { it.get<SharedRow>(1L).summary = "final" }
                        }
                    }

                    jpa.session { session -> session.get<SharedRow>(1L) }.let {
                        it.reviewer shouldBe "ada"
                        // The lost update, and the whole reason `@DynamicUpdate` exists: the flush
                        // wrote all three columns from the snapshot this session loaded, so the
                        // other transaction's column went back to what it had been.
                        it.summary shouldBe "draft"
                    }
                }
            }
        }

        feature("an update naming only what changed").config(enabled = JpaTestDatabase.available) {
            scenario("leaves the column it did not touch alone") {
                JpaTestDatabase.withJpa(Dossier::class) { jpa ->
                    jpa.transaction { session -> session.persist(Dossier(1, "draft", "nobody")) }

                    jpa.withSecondFactory(Dossier::class) { other ->
                        jpa.transaction { session ->
                            session.get<Dossier>(1L).reviewer = "ada"
                            other.transaction { it.get<Dossier>(1L).summary = "final" }
                        }
                    }

                    jpa.session { session -> session.get<Dossier>(1L) }.let {
                        it.reviewer shouldBe "ada"
                        it.summary shouldBe "final"
                    }
                }
            }
        }
    })
