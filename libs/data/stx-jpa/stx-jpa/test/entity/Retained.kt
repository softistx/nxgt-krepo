package com.softistx.jpa.entity

import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.SoftDelete

/*
 * Rows that outlive their deletion, values that refuse a stale write, and the two shapes a column
 * takes when it is not a scalar of its own.
 *
 * None of this was exercised. Each one is a separate question about whether a *blocking* Hibernate
 * feature survives the reactive session, and none of them can be answered by reading the annotation.
 */

/** Hibernate's own soft delete, since 6.4 — it rewrites the delete and restricts every read. */
@Entity
@Table(name = "soft_notes")
@SoftDelete
class SoftNote(
    @Id var id: Long = 0,
    var text: String = "",
)

/**
 * The older hand-written form, written the way anyone would write it — and it does not work here.
 *
 * Hibernate hands `@SQLDelete` to the driver **untouched**, so the statement carries neither of the
 * two things Hibernate's own SQL gets for free. This one fails on the first: there is no JDBC under
 * the Vert.x pool to read a `?`, so the server is sent a literal one and answers
 * `syntax error at end of input`. [LegacyDollarNote] fails on the second.
 */
@Entity
@Table(name = "legacy_notes")
@SQLDelete(sql = "update legacy_notes set archived = true where id = ?")
@SQLRestriction("archived = false")
class LegacyNote(
    @Id var id: Long = 0,
    var text: String = "",
    var archived: Boolean = false,
)

/**
 * The same thing with the placeholder corrected, which only uncovers the other half.
 *
 * The name is not schema-qualified, and nothing will qualify it: `JpaConfig.schema` is a runtime
 * value and an annotation is a compile-time constant. So `@SQLDelete` and `@SQLRestriction` are
 * unusable together with a configured schema — the same trap the README already records for
 * `nativeMutate`. `@SoftDelete` has neither problem, because Hibernate generates its statement.
 */
@Entity
@Table(name = "dollar_notes")
@SQLDelete(sql = "update dollar_notes set archived = true where id = \$1")
@SQLRestriction("archived = false")
class LegacyDollarNote(
    @Id var id: Long = 0,
    var text: String = "",
    var archived: Boolean = false,
)

/** Optimistic locking. The interesting half is what a lost update looks like from a coroutine. */
@Entity
@Table(name = "counters")
class Counter(
    @Id var id: Long = 0,
    var total: Long = 0,
    @Version var version: Long = 0,
)

enum class TicketState { OPEN, CLOSED }

@Entity
@Table(name = "labelled")
class Labelled(
    @Id var id: Long = 0,
    @Enumerated(EnumType.STRING) var state: TicketState = TicketState.OPEN,
    @ElementCollection var tags: MutableSet<String> = mutableSetOf(),
)
