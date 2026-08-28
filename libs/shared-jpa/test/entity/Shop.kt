package com.strange.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Transient

/*
 * Three entities with real associations between them, for the specs that need a join to be a join
 * rather than a second column. [Thing] is deliberately flat and stays that way; a query DSL cannot
 * be exercised against it.
 *
 * `Purchase` rather than `Order` because `order` is reserved in SQL and every one of these tables is
 * created for real by a spec.
 */

@Entity
@Table(name = "buyers")
class Buyer(
    @Id var id: Long = 0,
    var name: String = "",
    var tier: String? = null,
)

@Entity
@Table(name = "purchases")
class Purchase(
    @Id var id: Long = 0,
    var reference: String = "",
    var total: Long = 0,
    @ManyToOne var customer: Buyer? = null,
    @OneToMany(mappedBy = "purchase") var lines: MutableList<PurchaseLine> = mutableListOf(),
) {
    /** Comparable, sortable by the DSL's types, and not a column — which `PagingTest` needs. */
    @Transient
    val label: String = ""
}

@Entity
@Table(name = "purchase_lines")
class PurchaseLine(
    @Id var id: Long = 0,
    var sku: String = "",
    @ManyToOne var purchase: Purchase? = null,
)
