package com.strange.koin.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * The one entity `jpaModule`'s scenario maps.
 *
 * Never stored: the spec runs with `SchemaMode.NONE`, so no table is created and nothing on the
 * server is touched. It exists because a factory has to be told about at least one class.
 */
@Entity
@Table(name = "entries")
class Entry(
    @Id var id: Long = 0,
    var label: String = "",
)
