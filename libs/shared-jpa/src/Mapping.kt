package com.strange.jpa

import jakarta.persistence.Id
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Refuses a `kotlin.uuid.Uuid` identifier, at the one moment it can still be refused.
 *
 * Everywhere else in an entity a Kotlin `Uuid` is fine — `UuidConverter` maps it to Postgres' own
 * `uuid`. An `@Id` is the exception, and not one this module can work around: Hibernate rejects an
 * `AttributeConverter` on an identifier outright (*"'AttributeConverter' not allowed for attribute
 * 'id' annotated '@jakarta.persistence.Id'"*), and the JDBC-bound `UserType` that would otherwise
 * answer it is exactly what Hibernate Reactive's own documentation says not to reach for.
 *
 * What happens without this check is the reason for it. Nothing fails: Hibernate falls back to
 * serializing the unmapped type, the schema comes out with a `bytea` primary key, inserts and reads
 * both work, and the table is unreadable to every other client of that database. It is the same
 * failure `UuidConverter` exists to prevent, in the one place a converter cannot reach.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun rejectUuidIdentifiers(entities: List<KClass<*>>) {
    entities.forEach { entity ->
        generateSequence(entity.java) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { it.isAnnotationPresent(Id::class.java) && it.type == Uuid::class.java }
            .forEach { field ->
                error(
                    "${entity.simpleName}.${field.name} is a kotlin.uuid.Uuid identifier, which cannot be mapped: " +
                        "Hibernate does not allow an AttributeConverter on an @Id, so the column would silently " +
                        "become bytea. Use java.util.UUID for the identifier — kotlin.uuid.Uuid is fine everywhere else.",
                )
            }
    }
}
