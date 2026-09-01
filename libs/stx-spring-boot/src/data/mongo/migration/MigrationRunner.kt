package com.softistx.spring.data.mongo.migration

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.AnnotationUtils
import kotlin.time.Clock

/**
 * Runs every [Migration] bean once, in order, after the application is ready.
 *
 * **The class name carries the version.** A unit is named `<prefix><order><name>` — `V3Currencies`
 * with the default prefix — and `<name>` must start with a letter or an underscore. That last part
 * is not decoration: with a name of `[A-Za-z0-9_]+` and an order of `[0-9]+`, `V102` could be read
 * as order 102 or order 10 followed by `2`, and the regex would pick one silently.
 *
 * **A bean that does not match is a loud warning, not a shrug.** A migration that quietly does not
 * run is the failure this whole mechanism exists to prevent, so a misnamed one says so at startup.
 *
 * **Two units at the same order abort the whole run.** Their [MigrationEntry.code] would collide, so
 * one of them would be recorded as the other and never run. Which one is arbitrary, and running
 * migrations in an arbitrary order is worse than running none.
 *
 * **A failure stops everything after it.** Migrations are usually written against the state the
 * previous one left, so continuing past a failure applies a change to a database that is not in the
 * shape it expects. The failed record stays `FAILED` until somebody deals with it, and every
 * subsequent startup refuses to run anything.
 *
 * **Migrations should be idempotent.** The unique index on `code` stops two instances from both
 * recording a migration, but nothing here holds a lock while one runs, so two instances starting at
 * the same moment can both execute the same `PENDING` unit. Making that impossible needs a lease
 * with a timeout, and a lease that expires while a long migration is still running is a worse
 * failure than the one it prevents.
 */
class MigrationRunner(
    private val context: ApplicationContext,
    private val store: MigrationStore,
    private val prefix: String,
) {
    private val log = LoggerFactory.getLogger(MigrationRunner::class.java)

    private val naming = Regex("^${Regex.escape(prefix)}(\\d+)([A-Za-z_][A-Za-z0-9_]*)$")

    @EventListener(ApplicationReadyEvent::class)
    suspend fun onApplicationReady() {
        runCatching { run() }
            .onFailure { log.error("stx.data.mongo.migration: the run did not finish", it) }
    }

    /** Applies what has not been applied, and returns the record of every unit it knows about. */
    suspend fun run(): List<MigrationEntry> {
        val units = units() ?: return emptyList()
        if (units.isEmpty()) return emptyList()

        store.prepare()
        var halted = store.anyFailed()
        if (halted) log.error("stx.data.mongo.migration: a migration is recorded as FAILED; running none")

        return units.map { unit ->
            val entry = record(unit)
            when {
                entry.status != MigrationStatus.PENDING -> {
                    entry
                }

                halted -> {
                    entry
                }

                else -> {
                    apply(unit, entry).also {
                        if (it.status == MigrationStatus.FAILED) halted = true
                    }
                }
            }
        }
    }

    /** The existing record for a unit, or a new `PENDING` one. */
    private suspend fun record(unit: DiscoveredMigration): MigrationEntry =
        store.find(unit.code)
            ?: store.claim(
                MigrationEntry(
                    code = unit.code,
                    order = unit.order,
                    description = unit.description,
                ),
            )
            // Another instance inserted it between the find and the claim; its record is the record.
            ?: checkNotNull(store.find(unit.code)) { "${unit.code} was claimed and then vanished" }

    private suspend fun apply(
        unit: DiscoveredMigration,
        entry: MigrationEntry,
    ): MigrationEntry {
        log.info("stx.data.mongo.migration: applying {} — {}", unit.code, unit.description)
        return runCatching { unit.migration.migrate() }
            .fold(
                onSuccess = { store.update(entry.copy(status = MigrationStatus.APPLIED, updatedAt = Clock.System.now())) },
                onFailure = { failure ->
                    log.error("stx.data.mongo.migration: {} failed; nothing after it will run", unit.code, failure)
                    store.update(
                        entry.copy(
                            status = MigrationStatus.FAILED,
                            failure = failure.message ?: failure::class.qualifiedName,
                            updatedAt = Clock.System.now(),
                        ),
                    )
                },
            )
    }

    /**
     * Every migration bean, in order — or null when two of them claim the same order.
     *
     * Discovered by type rather than by `@MigrationUnit`, so a unit declared through an `@Bean`
     * method runs like one that was component-scanned.
     */
    internal fun units(): List<DiscoveredMigration>? {
        val units =
            context
                .getBeansOfType(Migration::class.java)
                .values
                .mapNotNull { migration ->
                    val name = migration.name()
                    val match = naming.matchEntire(name)
                    if (match == null) {
                        log.warn(
                            "stx.data.mongo.migration: {} is a Migration bean but is not named {}<order><Name>; it will not run",
                            name,
                            prefix,
                        )
                        return@mapNotNull null
                    }
                    val (order, tail) = match.destructured
                    DiscoveredMigration(
                        migration = migration,
                        order = order.toInt(),
                        code = "$prefix$order",
                        description = migration.description().ifBlank { tail },
                    )
                }.sortedBy { it.order }

        val duplicates = units.groupBy { it.order }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            log.error(
                "stx.data.mongo.migration: two migrations share an order ({}); running none until that is resolved",
                duplicates.keys.joinToString(),
            )
            return null
        }
        return units
    }

    /**
     * The bean's own class name, past any proxy.
     *
     * `@MigrationUnit` is `@Component`, so a unit that also carries `@Transactional` or an aspect is
     * a CGLIB subclass named `V3Currencies$$SpringCGLIB$$0` — and the version is in the part before
     * the `$$`.
     */
    private fun Migration.name(): String = this::class.java.simpleName.substringBefore("$$")

    /**
     * `@MigrationUnit`'s description, found through the proxy.
     *
     * `AnnotationUtils.findAnnotation` rather than `getAnnotation`: the annotation is not
     * `@Inherited`, so a CGLIB subclass does not carry it and the direct lookup returns null on
     * exactly the units that were proxied.
     */
    private fun Migration.description(): String =
        AnnotationUtils
            .findAnnotation(this::class.java, MigrationUnit::class.java)
            ?.description
            .orEmpty()
}

/** A migration bean and what its name says about it. */
internal data class DiscoveredMigration(
    val migration: Migration,
    val order: Int,
    val code: String,
    val description: String,
)
