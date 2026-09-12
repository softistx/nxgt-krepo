package com.softistx.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.SecondaryTable
import jakarta.persistence.Table
import org.hibernate.annotations.DynamicUpdate

/*
 * One entity over two tables, and one entity whose update names only what changed.
 *
 * Both are statements about the SQL Hibernate *generates*, which is why neither can be checked by
 * persisting a value and reading it back — that measurement passes whatever the generator did. Each
 * one here has a witness that does not go through the mapping: the catalog for the second table, and
 * a lost update for the dynamic one.
 */

/**
 * A row split across two tables, joined on the identifier.
 *
 * The interesting question is not whether it works but what it costs: `@SecondaryTable` is a join on
 * every read of the entity, paid whether or not the caller wanted the second table's columns, and
 * there is no `LAZY` for it. `SpreadTest` measures the join rather than describing it.
 */
@Entity
@Table(name = "passports")
@SecondaryTable(name = "passport_photos", pkJoinColumns = [PrimaryKeyJoinColumn(name = "passport_id")])
class Passport(
    @Id var id: Long = 0,
    var holder: String = "",
    @Column(table = "passport_photos") var photo: String = "",
)

/**
 * An entity whose `update` names only the columns that changed.
 *
 * Off by default: Hibernate writes **every** column on every update, because one prepared statement
 * per entity caches better than one per set of dirty fields. The cost of that default is a lost
 * update between two sessions that touched different columns of the same row, and that is the
 * witness [SharedRow] provides — no SQL logging required.
 */
@Entity
@Table(name = "dossiers")
@DynamicUpdate
class Dossier(
    @Id var id: Long = 0,
    var summary: String = "",
    var reviewer: String = "",
)

/** The same three columns with nothing said, so the two can be compared. */
@Entity
@Table(name = "shared_rows")
class SharedRow(
    @Id var id: Long = 0,
    var summary: String = "",
    var reviewer: String = "",
)
