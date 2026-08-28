package com.strange.jpa.dsl

import com.strange.jpa.JpaMappingException
import jakarta.persistence.EntityGraph
import jakarta.persistence.Graph
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.ManagedType
import jakarta.persistence.metamodel.Metamodel
import jakarta.persistence.metamodel.PluralAttribute
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * A fetch plan: what to load, said once and applied wherever it is needed.
 *
 * ```kotlin
 * val withBuyer = session.entityGraph<Purchase> { add(Purchase::customer) }
 *
 * session.find(1L, withBuyer)                       // the thing a fetch join cannot do
 * session.select<Purchase>().graph(withBuyer).list()
 * ```
 *
 * **It is not another spelling of a fetch join.** It reaches the two places a fetch join cannot.
 * The first is `find`: loading by identifier has no query to hang a join on, so before this the only
 * way to read an association off a row you had the id of was to write a `select` instead — and in a
 * reactive session an unfetched `LAZY` association does not cost a second select, it throws. The
 * second is depth: a fetch join goes one level by design, and a [GraphScope.subgraph] nests as far
 * as the mapping does.
 *
 * The third thing it is good at has no equivalent at all: a plan is a *value*. It is named, held in
 * a `val`, passed to a repository, and applied to a `find` and a `select` that then cannot disagree
 * about what a "purchase with its buyer" is.
 *
 * **Hibernate applies it as a fetch graph, not a load graph** — pinned by `EntityGraphTest`. An
 * association the mapping declares `EAGER` and the plan does not name becomes lazy for that query,
 * and reading it then throws. That is one more reason for the rule in this module's README: mark
 * every association `LAZY` and say what each query needs, and the distinction stops mattering
 * because there is nothing eager left to lose.
 *
 * [raw] is the JPA object, for whatever this does not wrap. [type] is what it plans for, which is
 * how `get` names the entity it did not find.
 */
class JpaEntityGraph<T : Any> internal constructor(
    /** The entity this plans for. */
    val type: KClass<T>,
    /** Hibernate's own graph, for the Criteria and the hints this does not wrap. */
    val raw: EntityGraph<T>,
    /**
     * Whether anything in the plan is a collection, which is what makes `limit`, `offset` and `page`
     * unsafe on a query using it — for the reason [Fetches.fetchEach] carries.
     */
    internal val holdsCollection: Boolean,
)

/** The one flag the whole tree of scopes shares, since a nested collection is the root's problem. */
internal class GraphPlan {
    var holdsCollection: Boolean = false
}

/**
 * A fetch plan being built, from the entity's own properties rather than from attribute names.
 *
 * ```kotlin
 * session.entityGraph<Purchase> {
 *     add(Purchase::customer)
 *     subgraphEach(Purchase::lines) { add(PurchaseLine::product) }
 * }
 * ```
 *
 * JPA's own spelling is `addAttributeNodes("customer")` and `addSubgraph("lines")` — strings,
 * unchecked until the query runs, which is the thing `com.strange.jpa.dsl` exists to replace.
 *
 * **[add] and [addEach] are the same JPA call and two names here on purpose.** The plan has to know
 * whether it holds a collection, because that is what makes a page over it silently wrong, and
 * `KProperty1` is covariant in its value so `add(Purchase::lines)` would type-check and slip past.
 * So each is checked against the mapping and refuses the other's argument by name. It also means a
 * misspelled or unmapped attribute fails here, naming the entity, rather than inside Hibernate.
 */
@JpaDsl
class GraphScope<T : Any> internal constructor(
    private val metamodel: Metamodel,
    private val managed: ManagedType<T>,
    private val graph: Graph<T>,
    private val plan: GraphPlan,
) {
    /** Loads these to-one associations, or these basic attributes. */
    fun add(vararg properties: KProperty1<T, *>): GraphScope<T> = apply { properties.forEach { node(it.name, plural = false) } }

    /**
     * Loads these collections, whole.
     *
     * A plan that does this makes `limit`, `offset` and `page` refuse on the query it is applied to,
     * because the database cuts the joined rows rather than the owners.
     */
    fun addEach(vararg properties: KProperty1<T, out Collection<*>>): GraphScope<T> =
        apply { properties.forEach { node(it.name, plural = true) } }

    /** Loads a to-one association and, inside the block, what to load from *it*. */
    fun <V : Any> subgraph(
        property: KProperty1<T, V?>,
        block: GraphScope<V>.() -> Unit,
    ): GraphScope<T> =
        apply {
            val attribute = attribute(property.name, plural = false)
            nested(attribute.javaType, graph.addSubgraph(property.name), block)
        }

    /** The same over a collection: what to load from each element. */
    @Suppress("UNCHECKED_CAST")
    fun <E : Any> subgraphEach(
        property: KProperty1<T, Collection<E>>,
        block: GraphScope<E>.() -> Unit,
    ): GraphScope<T> =
        apply {
            // Checked to be plural on the line above, so it is a PluralAttribute — which is the type
            // `addElementSubgraph` needs, and the one carrying the element's class.
            val attribute = attribute(property.name, plural = true) as PluralAttribute<in T, *, E>
            plan.holdsCollection = true
            nested(attribute.elementType.javaType, graph.addElementSubgraph(attribute), block)
        }

    private fun node(
        name: String,
        plural: Boolean,
    ) {
        if (attribute(name, plural).isCollection) plan.holdsCollection = true
        graph.addAttributeNodes(name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <V : Any> nested(
        target: Class<*>,
        subgraph: Graph<V>,
        block: GraphScope<V>.() -> Unit,
    ) {
        // The target's own class comes off the mapping rather than off the property, so this needs
        // no kotlin-reflect — the same reason `JpaRepository` reads its identifier type that way.
        val type =
            runCatching { metamodel.managedType(target) as ManagedType<V> }
                .getOrElse {
                    throw JpaMappingException(
                        "${target.simpleName} is not a mapped type, so there is nothing to load from it",
                    )
                }
        GraphScope(metamodel, type, subgraph, plan).apply(block)
    }

    private fun attribute(
        name: String,
        plural: Boolean,
    ): Attribute<in T, *> {
        val entity = managed.javaType.simpleName
        val attribute =
            runCatching { managed.getAttribute(name) }
                .getOrElse { throw JpaMappingException("$entity has no mapped attribute '$name' to load") }

        if (attribute.isCollection != plural) {
            throw JpaMappingException(
                if (plural) {
                    "'$name' on $entity is not a collection: say add($entity::$name)"
                } else {
                    "'$name' on $entity is a collection: say addEach($entity::$name), so a query " +
                        "using this plan refuses to page rather than cutting the collection silently"
                },
            )
        }
        return attribute
    }
}

/**
 * A fetch plan for [T], built from its properties — see [JpaEntityGraph].
 *
 * ```kotlin
 * val withBuyer = session.entityGraph<Purchase> { add(Purchase::customer) }
 * ```
 *
 * On the session rather than on `Stage.QueryProducer`, which is where the rest of the DSL hangs,
 * because building one needs the metamodel to check each attribute and only a session has the
 * factory. A plan outlives the session that built it: it is keyed to the factory's mapping, so one
 * built at startup can be applied by every request.
 *
 * There is no `KClass` twin, unlike `select` and `project`: those have one because a class generic
 * in its entity cannot reify — `JpaRepository` is the example — and nothing generic needs to *build*
 * a plan. A repository is handed one.
 */
inline fun <reified T : Any> Stage.Session.entityGraph(noinline block: GraphScope<T>.() -> Unit): JpaEntityGraph<T> =
    graphOf(this, factory.metamodel, T::class, block)

/** The same, on a stateless session. */
inline fun <reified T : Any> Stage.StatelessSession.entityGraph(noinline block: GraphScope<T>.() -> Unit): JpaEntityGraph<T> =
    graphOf(this, factory.metamodel, T::class, block)

/** What the two above are, once the type argument has become a `Class` for Hibernate. */
@PublishedApi
internal fun <T : Any> graphOf(
    producer: Stage.QueryProducer,
    metamodel: Metamodel,
    type: KClass<T>,
    block: GraphScope<T>.() -> Unit,
): JpaEntityGraph<T> {
    val entity =
        runCatching { metamodel.entity(type.java) }
            .getOrElse { throw JpaMappingException("${type.simpleName} is not a mapped entity, so it has no fetch plan") }

    val graph = producer.createEntityGraph(type.java)
    val plan = GraphPlan()
    GraphScope(metamodel, entity, graph, plan).apply(block)
    return JpaEntityGraph(type, graph, plan.holdsCollection)
}
