package com.strange.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * What the naming scenarios read: one property nobody named, one named by hand, and one whose name
 * has an acronym in it.
 */
@Entity
@Table(name = "audited")
class Audited(
    @Id var id: Long = 0,
    var createdBy: String = "",
    var orderURL: String = "",
    @Column(name = "lastSeen") var lastSeen: String = "",
)

/** The same, with no `@Table`, so the table name is one Hibernate decides too. */
@Entity
class AuditedEvent(
    @Id var id: Long = 0,
    var happenedAt: String = "",
)
