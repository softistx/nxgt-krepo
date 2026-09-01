package com.softistx.spring.data.mongo.migration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Registers a [Migration] as a bean, and says what it does.
 *
 * `@Component`, so a migration in a scanned package needs nothing else, and
 * `@ConditionalOnProperty`, so migrations are not even constructed when
 * `stx.data.mongo.migration.enabled` is off — several of them inject a template and a repository
 * each, and building all of that for a feature nobody turned on is waste at every startup.
 *
 * **[description] is the only thing this contributes to the run.** [MigrationRunner] discovers units
 * by type, not by this annotation, so a `Migration` bean declared some other way — an `@Bean` method,
 * a Koin-style factory — still runs. The version this was extracted from filtered on the annotation
 * and silently ignored anything without it, which is a migration that does not happen and does not
 * say so.
 */
@Component
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(prefix = "stx.data.mongo.migration", name = ["enabled"], havingValue = "true")
annotation class MigrationUnit(
    /** What this migration does, recorded on its [MigrationEntry]. Defaults to the class's name. */
    val description: String = "",
)
