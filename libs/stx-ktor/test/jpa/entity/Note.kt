package com.softistx.ktor.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** The entity [JpaPluginTest] maps, stores and reads back through a route. */
@Entity
@Table(name = "notes")
class Note(
    @Id var id: Long = 0,
    var text: String = "",
)
