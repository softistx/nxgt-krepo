package com.strange.jpa.dsl

import com.strange.jpa.JpaUnrestrictedMutationException
import com.strange.jpa.query.JpaMutation
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KClass

/**
 * A bulk statement being built: what it changes or removes, and to which rows.
 *
 * ```kotlin
 * session
 *     .update<Purchase> { set(Purchase::total, 0L) }
 *     .where { Purchase::customer.isNull() }
 *     .execute()
 * ```
 *
 * It goes straight to the database, past everything the session knows: no cascade fires, no
 * `@PreUpdate` or `@PreRemove` runs, and an entity already loaded in this session keeps the values
 * it had. That is Hibernate's rule for a bulk statement rather than this module's.
 *
 * **A statement with nothing restricting it is refused** unless [everyRow] was said, and refused at
 * [execute] rather than earlier — the restrictions arrive down the chain, so there is no point at
 * which "no `where` yet" means "no `where` ever" until the statement is about to run. HQL allows
 * `delete from Purchase` and so does this, out loud, because a `where` adds nothing when its block
 * answers null and a statement whose every filter turned out not to apply would otherwise be a
 * statement against the whole table.
 *
 * It cannot join. That is JPA's rule for a bulk statement, which is why the scope is a [Filters] and
 * not a [Joins] — a join here would compile and then fail when Hibernate rendered it.
 */
@JpaDsl
abstract class MutationScope<T : Any, SELF : MutationScope<T, SELF>> internal constructor(
    internal val producer: Stage.QueryProducer,
    private val entity: KClass<T>,
    final override val from: Root<T>,
) : Filters<T> {
    private val restrictions = mutableListOf<Predicate>()
    private var unrestricted = false

    @Suppress("UNCHECKED_CAST")
    private val self: SELF get() = this as SELF

    /** Restricts the statement. Called more than once, the restrictions are `and`ed together. */
    fun where(block: SELF.() -> Predicate?): SELF = self.also { scope -> scope.block()?.let { restrictions += it } }

    /** Says that every row is meant, which is the only way to run a statement without a [where]. */
    fun everyRow(): SELF = self.also { unrestricted = true }

    /** Runs it, and answers with how many rows it touched. */
    suspend fun execute(): Int {
        if (restrictions.isEmpty() && !unrestricted) throw JpaUnrestrictedMutationException(entity, verb)
        return JpaMutation(query(restrictions)).execute()
    }

    /** What this is called in an error message — `update` or `delete`. */
    internal abstract val verb: String

    /**
     * The statement, restricted and ready to run.
     *
     * The subclass's job rather than this one's: `CriteriaUpdate` and `CriteriaDelete` share no
     * supertype that carries either `where` or a `createMutationQuery` overload, so there is nothing
     * here that could apply the restrictions to both.
     */
    internal abstract fun query(restrictions: List<Predicate>): Stage.MutationQuery
}
