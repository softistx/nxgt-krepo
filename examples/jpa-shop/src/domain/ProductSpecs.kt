package com.strange.example.shop.domain

import com.strange.jpa.criteria.JpaSpec
import com.strange.jpa.criteria.eq
import com.strange.jpa.criteria.get
import com.strange.jpa.criteria.ilike
import com.strange.jpa.criteria.le

/**
 * Restrictions worth a name, because more than one query asks for them.
 *
 * A [JpaSpec] is `(Root<Product>) -> Predicate?` and nothing more — a plain function type over
 * Criteria's own root, so these compose with `and` and drop into any read on the session or into a
 * hand-written criteria unchanged. Naming a filter needs no framework, only a function that returns
 * one.
 */
object ProductSpecs {
    val available: JpaSpec<Product> = { it[Product::discontinued] eq false }

    fun named(term: String): JpaSpec<Product> = { it[Product::name] ilike "%$term%" }

    fun upTo(cents: Long): JpaSpec<Product> = { it[Product::priceInCents] le cents }
}
