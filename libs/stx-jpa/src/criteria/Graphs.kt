package com.strange.jpa.criteria

import jakarta.persistence.EntityGraph
import jakarta.persistence.Graph
import jakarta.persistence.Subgraph
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KProperty1

/**
 * An empty fetch plan for [T], to be filled in with [add] and [subgraph].
 *
 * ```kotlin
 * val plan = session.entityGraph<Purchase>()
 *     .add(Purchase::reference)
 *     .subgraphOf(Purchase::customer).add(Buyer::name).graph
 *
 * session.find(plan, id)
 * session.query<Purchase>("from Purchase").plan(plan).list()
 * ```
 *
 * A graph and a fetch join answer the same question — *load this association with its owner, in one
 * statement* — from opposite ends. A fetch join belongs to the query that wrote it; a graph is a
 * value, built once and applied to a `find`, a `get` or any number of queries. Reach for the graph
 * when the same plan is wanted in more than one place, and for [com.strange.jpa.criteria.fetch] when
 * it is one query's business.
 *
 * The reified form is the whole point: JPA spells this `createEntityGraph(Purchase::class.java)`,
 * and the class literal is the kind of thing that stops matching the variable it is assigned to.
 *
 * `Stage.QueryProducer` is the receiver, so this is the same call on a session and on a stateless
 * one — both declare `createEntityGraph`, and both take the result in `find`/`get`.
 */
inline fun <reified T : Any> Stage.QueryProducer.entityGraph(): EntityGraph<T> = createEntityGraph(T::class.java)

/**
 * Adds attributes to a graph, named by their properties.
 *
 * Returns the graph, so a plan reads as one expression. A property that is not an attribute of the
 * entity is a compile error rather than a runtime one, which is the reason to spell it this way and
 * not `addAttributeNodes("customer")`.
 */
fun <T : Any, G : Graph<T>> G.add(vararg properties: KProperty1<T, *>): G =
    apply { addAttributeNodes(*properties.map { it.name }.toTypedArray()) }

/**
 * A nested plan for a to-one association, so the graph goes as deep as the mapping does.
 *
 * The receiver is returned alongside it — `subgraphOf(Purchase::customer)` gives back both the
 * subgraph to fill in and the graph it was added to, so a chain can come back up. Use [Nested.graph]
 * to return to the parent and [Nested.add] to fill the child in.
 */
fun <T : Any, V : Any, G : Graph<T>> G.subgraphOf(property: KProperty1<T, V?>): Nested<V, G> = Nested(addSubgraph<V>(property.name), this)

/**
 * The same for a to-many association, whose nodes describe the *elements* and not the collection.
 *
 * JPA needs to be told which of the two is meant — `addSubgraph` on a plural attribute describes the
 * collection itself — so this calls `addElementSubgraph`, and the element type comes out of
 * `KProperty1<T, Collection<E>>` with no reflection at runtime.
 *
 * **A graph naming a collection truncates under a row limit**, exactly as a collection fetch join
 * does: the limit applies to the joined rows, so an owner comes back holding some of its elements.
 * [com.strange.jpa.criteria.fetchEach] has the measurement.
 */
fun <T : Any, E : Any, G : Graph<T>> G.subgraphEachOf(property: KProperty1<T, Collection<E>>): Nested<E, G> =
    Nested(addElementSubgraph<E>(property.name), this)

/**
 * A subgraph together with the graph it hangs off, so building a plan can descend and come back.
 *
 * Two properties rather than a lambda taking a receiver: a scope object would be a small DSL, and
 * the point of this package is that a plan is built out of ordinary calls a caller can also make
 * one at a time.
 */
class Nested<T : Any, out G : Graph<*>>(
    /** The nested plan, to name the association's own attributes on. */
    val subgraph: Subgraph<T>,
    /** The graph this was added to, to carry on naming its attributes. */
    val graph: G,
) {
    /** Adds attributes to [subgraph], and gives this back so the chain continues. */
    fun add(vararg properties: KProperty1<T, *>): Nested<T, G> = apply { subgraph.add(*properties) }

    /** A plan nested one level further, under [subgraph]. */
    fun <V : Any> subgraphOf(property: KProperty1<T, V?>): Nested<V, Subgraph<T>> = subgraph.subgraphOf(property)

    /** The same for a to-many association. */
    fun <E : Any> subgraphEachOf(property: KProperty1<T, Collection<E>>): Nested<E, Subgraph<T>> = subgraph.subgraphEachOf(property)
}
