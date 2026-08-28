package com.strange.jpa.dsl

import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType

/**
 * A join, indexed wherever the query needs it.
 *
 * ```kotlin
 * where { join(Purchase::customer)[Buyer::name] eq "ada" }
 *
 * val lines = joinEach(Purchase::lines, JoinType.LEFT)   // the same join, named
 * where { lines[PurchaseLine::sku] eq "abc" }
 * ```
 *
 * It is a [Joins] like the query scope itself, so joining from a join reads the same way as joining
 * from the root, and is remembered the same way: `join(Purchase::customer).join(Buyer::address)`.
 */
@JpaDsl
class JoinScope<P : Any, T : Any> internal constructor(
    /** The join underneath, for the Criteria this does not wrap. */
    override val from: Join<P, T>,
    internal val type: JoinType,
) : Joins<T> {
    override val joins: JoinRegistry<T> = JoinRegistry()
}
