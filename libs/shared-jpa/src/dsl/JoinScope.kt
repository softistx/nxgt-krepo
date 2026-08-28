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
 *
 * [Fetches.fetch] answers with one of these too, because Hibernate's fetch node is a join as well —
 * so a fetched association is filtered and indexed exactly like a joined one, and the query does not
 * take two joins to do both.
 */
@JpaDsl
class JoinScope<P : Any, T : Any> internal constructor(
    /** The join underneath, for the Criteria this does not wrap. */
    override val from: Join<P, T>,
    internal val type: JoinType,
    /** Whether this join also loads the association — see [Fetches]. */
    internal val fetched: Boolean = false,
) : Joins<T> {
    override val joins: JoinRegistry<T> = JoinRegistry()
}
