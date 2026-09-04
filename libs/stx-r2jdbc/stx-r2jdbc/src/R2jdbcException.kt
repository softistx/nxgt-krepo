package com.softistx.r2jdbc

/**
 * What this library throws when the failure is *its* to report — a URI it has no backend for, a
 * schema that is not there, a row asked for a column it does not have.
 *
 * A failure from the server keeps its own type. A `PgException` carries the SQLSTATE and a
 * `MySQLException` the vendor code, and both are more useful to a caller catching them than a
 * wrapper of ours would be: nothing here rethrows a driver exception in this type.
 */
class R2jdbcException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
