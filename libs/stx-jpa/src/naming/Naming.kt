package com.softistx.jpa.naming

import org.hibernate.boot.model.naming.ImplicitNamingStrategy

/**
 * What a column is called when the entity does not say.
 *
 * **A one-way door for any schema already in use**, which is why it is a named setting rather than a
 * default nobody sees: changing it renames every column that was not named by hand.
 */
enum class Naming(
    internal val strategy: ImplicitNamingStrategy?,
) {
    /**
     * `createdBy` becomes `created_by`. The default, because it is the case SQL is written in and
     * because the column is read by psql, by migrations and by whoever is looking at the database
     * without this application in front of them.
     *
     * A name the entity spells out is left exactly as written — see [SnakeCaseNaming].
     */
    SNAKE_CASE(SnakeCaseNaming()),

    /**
     * Hibernate's own behaviour: the property name, which Postgres then folds to lower case, so
     * `createdBy` is the column `createdby`.
     *
     * For mapping a schema that already exists and was not built this way.
     */
    AS_WRITTEN(null),
}
