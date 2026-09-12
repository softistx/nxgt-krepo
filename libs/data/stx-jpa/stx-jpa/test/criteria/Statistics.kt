package com.softistx.jpa.criteria

import com.softistx.jpa.Jpa

/**
 * How many entities [block] made Hibernate go back for — the N+1, counted.
 *
 * `prepareStatementCount` is the obvious counter and reads zero: it is a JDBC metric and there is no
 * JDBC under the Vert.x pool. `entityFetchCount` counts the loads a query did not ask for, which is
 * the number both specs using this exist to move.
 *
 * The factory has to have been built with `hibernate.generate_statistics` on, or this reads zero for
 * the wrong reason and every assertion passes.
 */
internal suspend fun <T> Jpa.secondaryFetches(block: suspend (Jpa) -> T): Long {
    factory.statistics.clear()
    block(this)
    return factory.statistics.entityFetchCount
}
