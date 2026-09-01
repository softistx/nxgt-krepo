package com.softistx.spring.data.mongo.audit

import com.softistx.spring.data.mongo.criteria.all
import com.softistx.spring.data.mongo.criteria.eq
import com.softistx.spring.data.mongo.criteria.query
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.exists
import org.springframework.data.mongodb.core.findOne

/**
 * Where audit entries are read and written.
 *
 * **A class over `ReactiveMongoTemplate`, not a `CoroutineCrudRepository`.** A repository interface
 * only becomes a bean when something scans for it, so shipping one would mean an application had to
 * point `@EnableReactiveMongoRepositories` at this library's package — a dependency reaching into
 * the application's configuration to be usable at all. Three queries do not justify that.
 *
 * **[audits] is passed to every call rather than written into `AuditEntry`'s `@Document`.** A
 * configurable mapped collection name is normally spelled `@Document("#{@environment.getProperty(…)}")`,
 * because the annotation is read at mapping time and no bean of ours runs early enough to rename it.
 * That expression needs a bean resolver, which means it only resolves inside an application context:
 * a `ReactiveMongoTemplate` built by hand — in a test, or in any code that wires its own — fails with
 * `EL1057E: No bean resolver registered`. The name is configuration, so it belongs to the bean that
 * holds it, and the template's `collectionName` overloads take it without any of that.
 */
class AuditStore(
    private val template: ReactiveMongoTemplate,
    private val audits: String = AuditEntry.COLLECTION,
) {
    /** The most recent entry for a document, or null when it has no history yet. */
    suspend fun latest(
        oid: String,
        collection: String,
    ): AuditEntry? =
        template
            .findOne<AuditEntry>(
                all("oid" eq oid, "collection" eq collection)
                    .query
                    .with(Sort.by(Sort.Order.desc("version")))
                    .limit(1),
                audits,
            ).awaitFirstOrNull()

    /** Whether this document has already been recorded as deleted. */
    suspend fun terminated(
        oid: String,
        collection: String,
    ): Boolean =
        template
            .exists<AuditEntry>(
                all("oid" eq oid, "collection" eq collection, "type" eq CommitType.TERMINAL.name).query,
                audits,
            ).awaitSingle()

    /** Appends an entry. Nothing here ever updates one — a history that can be edited is not one. */
    suspend fun append(entry: AuditEntry): AuditEntry = template.insert(entry, audits).awaitSingle()
}
