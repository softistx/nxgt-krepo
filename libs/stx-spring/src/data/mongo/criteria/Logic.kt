package com.strange.spring.data.mongo.criteria

import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * Every criterion has to hold.
 *
 * `Criteria().andOperator(...)` and not a chain of `.and("field")`, because the chained form builds
 * one document key per field and silently loses the second predicate on a field named twice:
 * `where("price").gt(5).and("price").lt(10)` is `{price: {$lt: 10}}`. An explicit `$and` keeps both.
 */
fun all(vararg criteria: Criteria): Criteria = Criteria().andOperator(*criteria)

/** Any one criterion is enough. */
fun any(vararg criteria: Criteria): Criteria = Criteria().orOperator(*criteria)

/** None of them may hold. */
fun none(vararg criteria: Criteria): Criteria = Criteria().norOperator(*criteria)

/** This criterion, as a query with nothing else on it. */
val Criteria.query: Query get() = Query(this)
