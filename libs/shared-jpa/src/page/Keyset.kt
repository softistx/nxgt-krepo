package com.strange.jpa.page

import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.query.criteria.HibernateCriteriaBuilder

/**
 * Everything strictly after [values] along [keys] — or strictly before, when the page runs backward.
 *
 * A single `>` only works when the ordering is one column. With more, "after" is lexicographic: the
 * first key is greater, *or* it is equal and the second is greater, and so on. There is no row-value
 * comparison in the criteria builder, so that expands to this `or` of `and`s — and it is why the
 * cursor carries every key rather than only the identifier.
 *
 * Each branch is `k1 = v1 and … and kn > vn`, which a composite index on the sort columns satisfies
 * with a seek. That is the whole point of paging this way: the cost does not grow with how deep the
 * page is, and no row is skipped or repeated when another is inserted between two requests.
 */
internal fun <T : Any> keysetPredicate(
    builder: HibernateCriteriaBuilder,
    root: Root<T>,
    keys: List<SortKey<T>>,
    values: List<Comparable<*>>,
    forward: Boolean,
): Predicate {
    val branches =
        keys.mapIndexed { index, key ->
            val equalities =
                keys.take(index).mapIndexed { earlier, previous ->
                    builder.equal(root.get<Any>(previous.name), values[earlier])
                }
            val comparison = compare(builder, root, key, values[index], forward)
            if (equalities.isEmpty()) comparison else builder.and(*(equalities + comparison).toTypedArray())
        }

    return builder.or(*branches.toTypedArray())
}

/** The ordering as the database wants it, every key flipped when the page runs backward. */
internal fun <T : Any> ordersOf(
    builder: HibernateCriteriaBuilder,
    root: Root<T>,
    keys: List<SortKey<T>>,
    forward: Boolean,
): List<Order> =
    keys.map { key ->
        val path = root.get<Any>(key.name)
        if (key.ascending == forward) builder.asc(path) else builder.desc(path)
    }

@Suppress("UNCHECKED_CAST")
private fun <T : Any> compare(
    builder: HibernateCriteriaBuilder,
    root: Root<T>,
    key: SortKey<T>,
    value: Comparable<*>,
    forward: Boolean,
): Predicate {
    // The key's type is the property's, which the caller has already had checked by the compiler;
    // by here both sides have been erased to Comparable and the cast only says so again.
    val path = root.get<Comparable<Any>>(key.name)
    val bound = value as Comparable<Any>
    return if (key.ascending == forward) builder.greaterThan(path, bound) else builder.lessThan(path, bound)
}
