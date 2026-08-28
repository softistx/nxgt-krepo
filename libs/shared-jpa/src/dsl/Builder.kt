package com.strange.jpa.dsl

import jakarta.persistence.criteria.Expression
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.sqm.tree.SqmNode

/**
 * The builder that made an expression, taken back off the expression itself.
 *
 * Every operator in this package needs one — `cb.greaterThan(path, value)` is the only way to say
 * `>` in Criteria — and there are two ways to give it one. Carrying the scope into each operator
 * forces all of them to be members of the scope class, which is one file for the whole vocabulary
 * and no way for a caller to write their own. Taking it off the node keeps them ordinary extensions
 * on [Expression], usable anywhere an expression is, in as many files as they need.
 *
 * The cast holds because every expression here comes from Hibernate's own builder, and Hibernate
 * builds `SqmNode`s — there is no second implementation of the criteria tree in the runtime. A spec
 * asserts it rather than this comment.
 */
internal val Expression<*>.builder: HibernateCriteriaBuilder
    get() = (this as SqmNode).nodeBuilder()
