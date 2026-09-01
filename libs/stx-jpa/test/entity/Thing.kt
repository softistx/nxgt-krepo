package com.softistx.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * The entity the specs in this module store and read back.
 *
 * Deliberately an ordinary Kotlin class: no `open`, no secondary constructor, no `data`. The
 * `noArg` and `allOpen` compiler plugins this module turns on supply what Hibernate needs, and this
 * fixture is where that claim is either true or false — if the module's settings did not reach the
 * test sources, nothing here would load.
 */
@Entity
@Table(name = "things")
class Thing(
    @Id var id: Long = 0,
    var name: String = "",
)
