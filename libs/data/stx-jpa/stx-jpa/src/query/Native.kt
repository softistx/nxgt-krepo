package com.softistx.jpa.query

import org.hibernate.reactive.stage.Stage
import org.intellij.lang.annotations.Language

/**
 * SQL, for the things HQL cannot say.
 *
 * ```kotlin
 * session.nativeQuery<String>("select name from things where name % :term")
 *     .parameter("term", term)
 *     .list()
 * ```
 *
 * Postgres has a great deal HQL has no syntax for — trigram similarity, `ilike`, window functions,
 * `jsonb` operators, `on conflict`. Reaching for this is not a failure; writing an entity query in
 * SQL when HQL would have said it is, because HQL is checked against the mapping at startup and this
 * is checked by the database at the moment it runs.
 *
 * **`JpaConfig.schema` does not reach this.** Hibernate renders HQL against the mapping and
 * qualifies the table itself; a native statement is sent as written, so on a connection whose
 * `search_path` does not include the configured schema it fails with *relation … does not exist*.
 * Qualify the table, or set the schema on the connection.
 *
 * [R] is the row: an entity when the columns are one table's, or a scalar for a projection.
 * Parameters bind exactly as they do in [query] — `:name`, never interpolation, and here it matters
 * more, since nothing between this string and the server will notice a quote in a value.
 */
inline fun <reified R> Stage.QueryProducer.nativeQuery(
    @Language("SQL") sql: String,
): JpaQuery<R> = JpaQuery({ sql }, createNativeQuery(sql, R::class.java))

/**
 * A SQL `insert`, `update` or `delete`, with the same caveat [mutate] carries: it goes straight to
 * the database, past everything the session knows.
 */
fun Stage.QueryProducer.nativeMutate(
    @Language("SQL") sql: String,
): JpaMutation = JpaMutation(createNativeMutationQuery(sql))
