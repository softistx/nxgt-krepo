package com.softistx.mongo.page

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.softistx.common.page.Page
import com.softistx.common.page.pageOf
import com.softistx.mongo.InvalidPaginationException
import com.softistx.mongo.query.findAll
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.codecs.DecoderContext
import org.bson.conversions.Bson

/**
 * One page of this collection, cut by keyset rather than by `skip`.
 *
 * `skip(n)` makes the server walk and discard n documents, so the cost of a page grows with how
 * deep it is and page 500 is a scan. Resuming from the previous page's sort key costs the same at
 * any depth, and — the part that actually shows up in production — it does not skip or repeat a
 * document when one is inserted between two requests.
 *
 * The query reads raw BSON and decodes each document twice over: once into `T` for the caller, and
 * once into a cursor. Reading `T` alone would leave nothing to build the cursor out of, since the
 * sort field need not be a property the entity exposes.
 *
 * One extra document is fetched beyond [PaginationOptions.limit]; whether it turned up is the whole
 * answer to "is there another page", and it costs one document instead of a second count query.
 */
suspend fun <T : Any> MongoCollection<T>.findPage(
    options: PaginationOptions,
    session: ClientSession? = null,
): Page<T> {
    val keys = sortKeys(options.sort)
    val filter = options.filter.toFilter()
    val query =
        options.cursor
            ?.let { Filters.and(filter, keysetFilter(decodeCursor(it, keys), keys, options.forward)) }
            ?: filter

    val limit = options.limit
    val documents =
        withDocumentClass<BsonDocument>()
            .findAll(query, session)
            .sort(sortOf(keys, options.forward))
            .let { if (limit == null) it else it.limit(limit + 1) }
            .toList()

    val codec = codecRegistry.get(documentClass)
    return pageOf(
        rows = documents,
        limit = limit,
        forward = options.forward,
        resumed = options.cursor != null,
        cursorOf = { encodeCursor(it, keys) },
        valueOf = { codec.decode(BsonDocumentReader(it), DecoderContext.builder().build()) },
    )
}

private fun JsonObject?.toFilter(): Bson =
    this?.let {
        runCatching { BsonDocument.parse(it.toString()) }
            .getOrElse { failure -> throw InvalidPaginationException("filter is not a valid query: ${failure.message}") }
    } ?: BsonDocument()
