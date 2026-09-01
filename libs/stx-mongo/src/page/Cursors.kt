package com.softistx.mongo.page

import com.softistx.mongo.InvalidPaginationException
import org.bson.BsonDocument
import org.bson.BsonNull
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import java.util.Base64

/**
 * A cursor is the sort key of the document it points at, in extended JSON, base64url-encoded.
 *
 * Extended JSON and not the relaxed kind, because the value has to come back as the *same* BSON
 * type it went out as — a date that returns as a string compares against nothing, and the page
 * after it would be empty rather than wrong, which is worse.
 *
 * Encoding is not encryption. A client can read a cursor and, if it likes, forge one; the filter it
 * builds is still a filter, so the worst it buys is a page starting somewhere else.
 */
private val EXTENDED = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()

internal fun encodeCursor(
    document: BsonDocument,
    keys: List<SortKey>,
): String {
    val key = BsonDocument()
    keys.forEach { key[it.field] = document[it.field] ?: BsonNull.VALUE }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(key.toJson(EXTENDED).toByteArray())
}

/**
 * The reverse, with the sort it was issued under checked — a cursor from a differently sorted
 * query would silently page along the wrong key.
 */
internal fun decodeCursor(
    cursor: String,
    keys: List<SortKey>,
): BsonDocument {
    val decoded =
        runCatching { BsonDocument.parse(String(Base64.getUrlDecoder().decode(cursor))) }
            .getOrElse { throw InvalidPaginationException("cursor is not one this query issued") }

    if (decoded.keys != keys.map { it.field }.toSet()) {
        throw InvalidPaginationException("cursor was issued for a different sort order")
    }

    return decoded
}
