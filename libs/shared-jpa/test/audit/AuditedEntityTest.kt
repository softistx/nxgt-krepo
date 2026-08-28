package com.strange.jpa.audit

import com.strange.jpa.Jpa
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Note
import com.strange.jpa.repository.JpaRepository
import com.strange.jpa.service.JpaCrudService
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Instant

internal data class NewNote(
    val id: Long,
    val text: String,
)

internal data class EditNote(
    val text: String? = null,
)

internal class NoteService(
    principal: String? = null,
) : JpaCrudService<Note, Long, NewNote, EditNote>(JpaRepository(Note::id), principal) {
    override suspend fun buildCreate(input: NewNote) = Note(input.id, input.text)

    override suspend fun applyUpdate(
        existing: Note,
        input: EditNote,
    ) {
        input.text?.let { existing.text = it }
    }
}

/** The audit trail: when, stamped by Hibernate inside the flush; who, stamped by the service. */
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
                    val service = NoteService()
                    jpa.transaction { session -> service.create(session, NewNote(1, "first")) }
                    val created = jpa.session { session -> service.findById(session, 1L) }

                    // The dirty check finds nothing to write, so no update is issued and @PreUpdate
                    // never runs — which a service comparing fields could not have told apart.
                    jpa.transaction { session -> service.update(session, 1L, EditNote(text = "first")) }

                    jpa.session { session -> service.findById(session, 1L) }.lastModifiedAt shouldBe created.lastModifiedAt
                }
            }

            scenario("but does move when a different principal touched it, which is the service's doing") {
                withJpa { jpa ->
                    jpa.transaction { session -> NoteService(principal = "ada").create(session, NewNote(1, "first")) }
                    val created = jpa.session { session -> NoteService().findById(session, 1L) }

                    // Nothing about the note changes — but `stampUpdated` assigns lastModifiedBy,
                    // and from a different principal that assignment is itself a change. So the
                    // dirty check finds one, @PreUpdate runs, and the timestamp moves. The scenario
                    // above passes only because its service has no principal to stamp.
                    jpa.transaction { session -> NoteService(principal = "bo").update(session, 1L, EditNote(text = "first")) }

                    val updated = jpa.session { session -> NoteService().findById(session, 1L) }
                    updated.lastModifiedBy shouldBe "bo"
                    updated.lastModifiedAt shouldBeGreaterThan created.lastModifiedAt
                }
            }
        }

        feature("who").config(enabled = JpaTestDatabase.available) {
            scenario("is stamped by the service, on both halves at creation") {
                withJpa { jpa ->
                    val service = NoteService(principal = "ada")
                    jpa.transaction { session -> service.create(session, NewNote(1, "first")) }

                    val note = jpa.session { session -> service.findById(session, 1L) }
                    note.createdBy shouldBe "ada"
                    note.lastModifiedBy shouldBe "ada"
                }
            }

            scenario("changes on the modified half only, and leaves the creator alone") {
                withJpa { jpa ->
                    jpa.transaction { session -> NoteService(principal = "ada").create(session, NewNote(1, "first")) }
                    jpa.transaction { session -> NoteService(principal = "bo").update(session, 1L, EditNote("second")) }

                    val note = jpa.session { session -> NoteService().findById(session, 1L) }
                    note.createdBy shouldBe "ada"
                    note.lastModifiedBy shouldBe "bo"
                }
            }

            scenario("is left empty when the service was told nobody") {
                withJpa { jpa ->
                    val service = NoteService()
                    jpa.transaction { session -> service.create(session, NewNote(1, "first")) }

                    val note = jpa.session { session -> service.findById(session, 1L) }
                    note.createdBy shouldBe ""
                    note.lastModifiedBy shouldBe ""
                    note.createdAt shouldBeGreaterThan epoch
                }
            }
        }
    })
