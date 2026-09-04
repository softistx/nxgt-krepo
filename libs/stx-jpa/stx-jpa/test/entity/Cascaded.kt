package com.softistx.jpa.entity

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

/*
 * Cascade owned by the database instead of by the persistence context.
 *
 * Worth its own fixtures because the two are not the same feature wearing different clothes: JPA's
 * cascade is Hibernate issuing extra statements from a *loaded* collection, and the database's is a
 * constraint firing on one statement with nothing loaded. The interesting part is what Hibernate does
 * when it is not told which of the two is in play.
 */

/** The FK carries `on delete cascade`, and Hibernate is told so. */
@Entity
@Table(name = "pallets")
class Pallet(
    @Id var id: Long = 0,
    @OneToMany(mappedBy = "pallet")
    @OnDelete(action = OnDeleteAction.CASCADE)
    var items: MutableList<PalletItem> = mutableListOf(),
)

@Entity
@Table(name = "pallet_items")
class PalletItem(
    @Id var id: Long = 0,
    var sku: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pallet_id", nullable = false)
    var pallet: Pallet? = null,
)

/** The same shape with nothing said: no JPA cascade, no `@OnDelete`, and a FK that refuses null. */
@Entity
@Table(name = "bins")
class Bin(
    @Id var id: Long = 0,
    @OneToMany(mappedBy = "bin")
    var items: MutableList<BinItem> = mutableListOf(),
)

@Entity
@Table(name = "bin_items")
class BinItem(
    @Id var id: Long = 0,
    var sku: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bin_id", nullable = false)
    var bin: Bin? = null,
)
