package com.strange.jpa.dsl

import com.strange.jpa.query.JpaQuery
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KProperty1

/**
 * A query being built: what restricts it, what orders it, and how to run it.
 *
 * ```kotlin
 * session
 *     .select<Purchase> {
 *         val buyer = join(Purchase::customer)      // declared once, read twice below
 *         where { buyer[Buyer::name] eq "ada" }
 *     }.where { Purchase::total gt 100L }
 *     .orderBy { desc(Purchase::total) }
 *     .limit(20)
 *     .list()
 * ```
 *
 * **The block and the chain are the same query, and `where` is the same `where`.** The block exists
 * for one reason: a join has to be held as a value to be read from more than one clause, and only a
 * block gives it somewhere to live. Everything else reads better chained, so nothing is forced into
 * the block — `select<Purchase>()` with no block at all is an ordinary way to start.
 *
 * **Every call adds; none replaces.** Two `where` calls are one `and`, two `orderBy` calls are two
 * sort keys in the order written, and a `where` may answer with null to add nothing — which is what
 * lets a query be assembled from conditions the caller learns one at a time, without the string
 * concatenation an HQL query would need.
 *
 * Nothing runs until a terminal. The criteria stays open until then, which is what allows the chain
 * to keep adding to it.
 */
@JpaDsl
abstract class QueryScope<T : Any, R : Any, SELF : QueryScope<T, R, SELF>> internal constructor(
    internal val producer: Stage.QueryProducer,
    internal val query: CriteriaQuery<R>,
    final override val from: Root<T>,
) : Joins<T> {
    private val restrictions = mutableListOf<Predicate>()

    internal val ordering = mutableListOf<Order>()
    internal val keys = mutableListOf<SortKey<T>>()

    private var limit: Int? = null
    private var offset: Int? = null
    private var readOnly = false

    @Suppress("UNCHECKED_CAST")
    private val self: SELF get() = this as SELF

    /** The root of the query, for the Criteria this does not wrap. */
    val root: Root<T> get() = from

    /** Restricts the query. Called more than once, the restrictions are `and`ed together. */
    fun where(block: SELF.() -> Predicate?): SELF = self.also { scope -> scope.block()?.let { restrictions += it } }

    /** Adds a sort key, after any already added. */
    fun orderBy(block: SELF.() -> Order): SELF = self.also { scope -> ordering += scope.block() }

    /**
     * Adds a sort key by property, which is what paging needs and ordinary queries may use too.
     *
     * The difference from [orderBy] is what can be read back: a cursor is the sort key of the row it
     * points at, so `page` can only resume along keys named this way. Without a `page` it is simply
     * an ordering.
     */
    fun <V : Comparable<in V>> sortBy(
        property: KProperty1<T, V>,
        descending: Boolean = false,
    ): SELF = self.also { keys += SortKey(property, ascending = !descending) }

    /**
     * Collapses duplicate rows.
     *
     * Worth reaching for after a [joinEach]: a join to a to-many association returns the owning row
     * once per element, and a query selecting the owner rarely means that.
     */
    fun distinct(distinct: Boolean = true): SELF = self.also { query.distinct(distinct) }

    /** At most this many rows. */
    fun limit(count: Int): SELF = self.also { limit = count }

    /** Skips this many rows first. Meaningless without an ordering, since nothing else fixes it. */
    fun offset(count: Int): SELF = self.also { offset = count }

    /**
     * Marks the results read-only, which is worth doing whenever they are.
     *
     * A stateful session keeps a snapshot of every entity it loads so it can work out at flush time
     * what changed. Read-only results skip the snapshot: half the memory, and no dirty check.
     */
    fun readOnly(readOnly: Boolean = true): SELF = self.also { this.readOnly = readOnly }

    /** Every matching row. */
    suspend fun list(): List<R> = built().list()

    /** The first row, or null — a limit of one, so the database stops looking after it. */
    suspend fun first(): R? = built().first()

    /** The one row there is, or `JpaNoResultException` / `JpaNonUniqueResultException`. */
    suspend fun single(): R = built().single()

    /** The one row there is, or null. More than one is still an error. */
    suspend fun singleOrNull(): R? = built().singleOrNull()

    /** How many rows this would return, ignoring [limit] and [offset] — the total a pager needs. */
    suspend fun count(): Long = built().count()

    private fun built(): JpaQuery<R> {
        val criteria = build()
        return JpaQuery({ criteria.hql() }, producer.createQuery(criteria))
            .apply {
                limit?.let { limit(it) }
                offset?.let { offset(it) }
                if (readOnly) readOnly()
            }
    }

    /**
     * The criteria this has been describing, with [extra] restricting it alongside the rest.
     *
     * Everything is *set* rather than added, so building twice says the same thing twice rather than
     * saying it twice over: a scope is a description, and a terminal reads it. That is what lets
     * [com.strange.jpa.page.page] hand in the keyset predicate for one page here and a different one
     * for the next, off the same query, without the two accumulating into a page of nothing. An
     * empty restriction clears the clause — `setRestriction` in Hibernate's `SqmQuerySpec` — which
     * is the half of it that would otherwise leave the last page's cursor behind.
     */
    internal open fun build(extra: Predicate? = null): CriteriaQuery<R> {
        query.where(*(restrictions + listOfNotNull(extra)).toTypedArray())
        val orders = ordering + keys.map { if (it.ascending) asc(it.property) else desc(it.property) }
        if (orders.isNotEmpty()) query.orderBy(orders)
        return query
    }
}
