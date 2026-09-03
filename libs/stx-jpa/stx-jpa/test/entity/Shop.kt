package com.softistx.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
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
 * **Every association is `LAZY`, including the to-ones.** JPA's default for `@ManyToOne` is EAGER,
 * which is a select per distinct owner behind any query returning more than one row — measured at
 * four for three rows in `FetchJoinTest`. These fixtures are what the specs read the rule off, so
 * they carry the mapping the module recommends rather than the one JPA defaults to.
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
    @ManyToOne(fetch = FetchType.LAZY) var customer: Buyer? = null,
    @OneToMany(mappedBy = "purchase") var lines: MutableList<PurchaseLine> = mutableListOf(),
) {
    /** Comparable, sortable by the DSL's types, and not a column — which `PagingTest` needs. */
    @Transient
    val label: String = ""

    /**
     * The association's own column, read a second time as a plain value.
     *
     * `insertable = false, updatable = false` is not decoration: it is what makes this a second
     * *view* of one column rather than a second column, and [RepeatedPurchase] is what happens
     * without it.
     * `customer` stays the writable side; this one is never written back, which is the trap
     * `ForeignKeyColumnTest` pins.
     *
     * It exists because reading `customer` to learn its id is exactly the throw a caller is trying
     * to avoid — a `@BatchMapping` keying a DataLoader has nothing else to key on.
     */
    @Column(name = "customer_id", insertable = false, updatable = false)
    var customerId: Long? = null
}

@Entity
@Table(name = "purchase_lines")
class PurchaseLine(
    @Id var id: Long = 0,
    var sku: String = "",
    @ManyToOne(fetch = FetchType.LAZY) var purchase: Purchase? = null,
)
