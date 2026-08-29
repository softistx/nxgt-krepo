package com.strange.mongo.query

import com.mongodb.MongoCommandException
import com.mongodb.client.model.IndexOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.conversions.Bson

/** `IndexOptionsConflict` and `IndexKeySpecsConflict` — the same index, declared differently. */
private val CONFLICT_CODES = setOf(85, 86)

/**
 * Creates the index unless an incompatible one is already there, in which case the existing index
 * stands and this returns null.
 *
 * Creating an index that already exists *identically* is a no-op in Mongo, so the only interesting
 * case is a conflict — and a conflict is not a failure a starting service should die on, nor one it
 * should resolve by itself: dropping and rebuilding an index on a live collection is a migration,
 * with a cost the caller has to choose to pay. A null return is that decision handed back.
 *
 * Everything else — no permission, no primary, a malformed key — is still thrown, which is what
 * separates this from a `try { } catch { println }`.
 */
suspend fun <T : Any> MongoCollection<T>.ensureIndex(
    key: Bson,
    options: IndexOptions = IndexOptions(),
): String? =
    try {
        createIndex(key, options)
    } catch (e: MongoCommandException) {
        if (e.errorCode in CONFLICT_CODES) null else throw e
    }

suspend fun <T : Any> MongoCollection<T>.ensureUniqueIndex(
    key: Bson,
    options: IndexOptions = IndexOptions(),
): String? = ensureIndex(key, options.unique(true))
