package com.strange.jpa.dsl

import jakarta.persistence.criteria.Join

/**
 * A join, kept as a value so it can be indexed wherever the query needs it.
 *
 * ```kotlin
 * val customer = join(Order::customer)
 * val lines = joinEach(Order::lines, JoinType.LEFT)
 * where { (customer[Customer::name] eq "ada") and (lines[Line::sku] eq "abc") }
 * ```
 *
 * It is a [Paths] like the query scope itself, so joining from a join reads the same way as joining
 * from the root: `customer.join(Customer::address)`.
 */
@JpaDsl
class JoinScope<P, T : Any> internal constructor(
    /** The join underneath, for the Criteria this does not wrap. */
    override val from: Join<P, T>,
) : Paths<T>
