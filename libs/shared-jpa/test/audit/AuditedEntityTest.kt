package com.strange.jpa.audit

import com.strange.jpa.Jpa
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Note
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Instant

/** The audit trail: when, stamped by Hibernate inside the flush; who, stamped by the caller. */
class AuditedEntityTest :
    FeatureSpec({

        val epoch = Instant.fromEpochSeconds(0)

        suspend fun <T> withJpa(block: suspend (Jpa) -> T): T = JpaTestDatabase.withJpa(Note::class, block = block)

        feature("when").config(enabled = JpaTestDatabase.available) {
            scenario("is stamped on insert, by Hibernate rather than by the caller") {
                withJpa { jpa ->
                    val before = Clock.System.now()
                    jpa.transaction { session -> session.persist(Note(1, "first")) }

                    val note = jpa.session { session -> session.get<Note>(1L) }

                    note.createdAt shouldBeGreaterThan before
                    note.lastModifiedAt shouldBe note.createdAt
                }
            }

            scenario("moves on an update, and creation stays where it was") {
                withJpa { jpa ->
                    jpa.transaction { session -> session.persist(Note(1, "first")) }
                    val created = jpa.session { session -> session.get<Note>(1L) }

                    jpa.transaction { session -> session.get<Note>(1L).text = "second" }
                    val updated = jpa.session { session -> session.get<Note>(1L) }

                    updated.createdAt shouldBe created.createdAt
                    updated.lastModifiedAt shouldBeGreaterThan created.lastModifiedAt
                }
            }

            scenario("does not move for an update that changed nothing") {
                withJpa { jpa ->
                    jpa.transaction { session -> session.persist(Note(1, "first")) }
                    val created = jpa.session { session -> session.get<Note>(1L) }

                    // The dirty check finds nothing to write, so no update is issued and @PreUpdate
                    // never runs — which a caller comparing fields could not have told apart.
                    jpa.transaction { session -> session.get<Note>(1L).text = "first" }

                    jpa.session { session -> session.get<Note>(1L) }.lastModifiedAt shouldBe created.lastModifiedAt
                }
            }

            scenario("but does move when only the principal changed, because that is a change too") {
                withJpa { jpa ->
                    jpa.transaction { session ->
                        session.persist(
                            Note(1, "first").apply {
                                createdBy = "ada"
                                lastModifiedBy = "ada"
                            },
                        )
                    }
                    val created = jpa.session { session -> session.get<Note>(1L) }

                    // Nothing else about the note changes — but assigning lastModifiedBy from a
                    // different principal is itself a change. So the dirty check finds one,
                    // @PreUpdate runs, and the timestamp moves. The scenario above passes only
                    // because nothing was assigned at all.
                    jpa.transaction { session -> session.get<Note>(1L).lastModifiedBy = "bo" }

                    val updated = jpa.session { session -> session.get<Note>(1L) }
                    updated.lastModifiedBy shouldBe "bo"
                    updated.lastModifiedAt shouldBeGreaterThan created.lastModifiedAt
                }
            }
        }

        feature("who").config(enabled = JpaTestDatabase.available) {
            scenario("is whatever the caller assigned, on both halves at creation") {
                withJpa { jpa ->
                    jpa.transaction { session ->
                        session.persist(
                            Note(1, "first").apply {
                                createdBy = "ada"
                                lastModifiedBy = "ada"
                            },
                        )
                    }

                    val note = jpa.session { session -> session.get<Note>(1L) }
                    note.createdBy shouldBe "ada"
                    note.lastModifiedBy shouldBe "ada"
                }
            }

            scenario("changes on the modified half only, and leaves the creator alone") {
                withJpa { jpa ->
                    jpa.transaction { session ->
                        session.persist(
                            Note(1, "first").apply {
                                createdBy = "ada"
                                lastModifiedBy = "ada"
                            },
                        )
                    }
                    jpa.transaction { session ->
                        session.get<Note>(1L).apply {
                            text = "second"
                            lastModifiedBy = "bo"
                        }
                    }

                    val note = jpa.session { session -> session.get<Note>(1L) }
                    note.createdBy shouldBe "ada"
                    note.lastModifiedBy shouldBe "bo"
                }
            }

            scenario("is left empty when nobody was named, while the timestamps still land") {
                withJpa { jpa ->
                    jpa.transaction { session -> session.persist(Note(1, "first")) }

                    val note = jpa.session { session -> session.get<Note>(1L) }
                    note.createdBy shouldBe ""
                    note.lastModifiedBy shouldBe ""
                    note.createdAt shouldBeGreaterThan epoch
                }
            }
        }
    })
