package com.softistx.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/*
 * One association left at JPA's default, so a spec can measure what that default costs.
 *
 * Everything else in the test tree is `LAZY`, which is what this module recommends. `@ManyToOne`
 * with nothing on it is EAGER, and eager over a query returning more than one row is a select per
 * distinct owner — the N+1 that `FetchJoinTest` counts against these two.
 */

@Entity
@Table(name = "warehouses")
class Warehouse(
    @Id var id: Long = 0,
    var name: String = "",
)

@Entity
@Table(name = "crates_stored")
class StoredCrate(
    @Id var id: Long = 0,
    var code: String = "",
    @ManyToOne @JoinColumn(name = "warehouse_id") var warehouse: Warehouse? = null,
)
