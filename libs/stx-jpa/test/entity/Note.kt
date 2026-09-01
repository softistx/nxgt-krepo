package com.softistx.jpa.entity

import com.softistx.jpa.audit.AuditedEntity
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** An entity that carries its own audit trail, for the specs that read one. */
@Entity
@Table(name = "notes")
class Note(
    @Id var id: Long = 0,
    var text: String = "",
) : AuditedEntity()
