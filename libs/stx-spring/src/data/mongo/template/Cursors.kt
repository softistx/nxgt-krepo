package com.strange.spring.data.mongo.template

import org.bson.BsonNull
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import java.util.Base64

/**
 * A cursor is the sort key of the document it points at, in extended JSON, base64url-encoded.
 *
 * **Extended and not relaxed JSON**, because the value has to come back as the *same* BSON type it
 * went out as. A date that returns as a string compares against nothing, and the page after it would
 * be empty rather than wrong — which is the harder failure to notice.
 *
 * Encoding is not encryption. A client can read a cursor and, if it likes, forge one; what it builds
 * is still a filter, so the worst that buys is a page starting somewhere else.
 *
 * `stx-mongo` has the same two functions over the driver's own `BsonDocument`. They are not shared
 * because the type that would have to be shared is BSON, and `stx-common` depends on kotlinx and
 * nothing else — a rule worth more than the twenty lines it costs here.
 */
private val EXTENDED: JsonWriterSettings = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()

internal fun encodeCursor(
    document: Document,
    keys: List<SortKey>,
): String {
    val key = Document()
    keys.forEach { key[it.field] = document[it.field] ?: BsonNull.VALUE }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(key.toJson(EXTENDED).toByteArray())
}

/**
 * The reverse, with the sort it was issued under checked.
 *
 * A cursor from a differently sorted query would page along the wrong key and answer with rows that
 * look plausible — so it is refused instead. That check is the reason the whole key is carried
 * rather than just the `_id`.
 */
internal fun decodeCursor(
    cursor: String,
    keys: List<SortKey>,
): Document {
    val decoded =
        runCatching { Document.parse(String(Base64.getUrlDecoder().decode(cursor))) }
            .getOrElse { throw invalidPage("cursor is not one this query issued") }

    if (decoded.keys != keys.map { it.field }.toSet()) {
        throw invalidPage("cursor was issued for a different sort order")
    }

    return decoded
}
