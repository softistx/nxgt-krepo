package com.softistx.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * An entity that exists to be **refused**, the way [Eager] exists to carry the mapping the module
 * does not recommend.
 *
 * It maps `customer_id` twice with both mappings writable — the association and a plain column —
 * which is the mistake anyone reaching for the foreign key makes first. `ForeignKeyColumnTest`
 * registers it in one scenario and records what Hibernate says, so `docs/jpa-mapping.md` can quote a
 * measured message instead of describing `insertable = false, updatable = false` as a convention.
 *
 * Never add it to another spec's entity list: `Jpa.connect` does not build with it.
 */
@Entity
@Table(name = "repeated_purchases")
class RepeatedPurchase(
    @Id var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY) var customer: Buyer? = null,
) {
    @Column
    var customerId: Long? = null
}
