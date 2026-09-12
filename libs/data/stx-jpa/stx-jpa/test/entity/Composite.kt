package com.softistx.jpa.entity

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Formula

/*
 * A key made of two columns, and a column the database computes.
 *
 * `@EmbeddedId` is the one an application meets first — a join table with a payload, an event keyed
 * by stream and sequence — and `find`/`get` take `Any` here, so whether a composite key reaches them
 * intact is a real question rather than a formality.
 */

@Embeddable
class LedgerKey(
    var stream: String = "",
    var sequence: Long = 0,
) {
    override fun equals(other: Any?): Boolean = other is LedgerKey && stream == other.stream && sequence == other.sequence

    override fun hashCode(): Int = 31 * stream.hashCode() + sequence.hashCode()
}

@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @EmbeddedId var key: LedgerKey = LedgerKey(),
    var payload: String = "",
)

/** A read-only column the database computes, which never appears in an insert or an update. */
@Entity
@Table(name = "rectangles")
class Rectangle(
    @jakarta.persistence.Id var id: Long = 0,
    var width: Int = 0,
    var height: Int = 0,
    @Formula("width * height") var area: Int = 0,
)
