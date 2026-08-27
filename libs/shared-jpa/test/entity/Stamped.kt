package com.strange.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An entity with one Kotlin `Instant` and one Kotlin `Uuid` on it, and nothing telling Hibernate
 * what to do with either.
 *
 * The absence is the fixture: no `@Convert`, no `@Column(columnDefinition = …)`. If the converters
 * were not registered by `Jpa.connect`, these two would still map — as serialized blobs — and every
 * scenario that only wrote and read back would still pass.
 */
@OptIn(ExperimentalUuidApi::class)
@Entity
@Table(name = "stamped")
class Stamped(
    @Id var id: Long = 0,
    var reference: Uuid = Uuid.NIL,
    var at: Instant = Instant.fromEpochSeconds(0),
)
