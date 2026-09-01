package com.softistx.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table

/**
 * An entity whose identifier is declared by a `@MappedSuperclass` rather than by itself.
 *
 * The fixture is the inheritance: `Ticket::id` is a reference to a property `Keyed` declares, and a
 * repository built from it has to end up querying `Ticket`. Getting that wrong would not fail — it
 * would query the wrong thing.
 */
@MappedSuperclass
abstract class Keyed(
    @Id var id: Long = 0,
)

@Entity
@Table(name = "tickets")
class Ticket(
    id: Long = 0,
    var subject: String = "",
) : Keyed(id)
