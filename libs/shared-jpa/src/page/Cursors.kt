package com.strange.jpa.page

import com.strange.jpa.JpaPaginationException
import com.strange.jpa.dsl.SortKey
import com.strange.jpa.json.jpaJson
import jakarta.persistence.criteria.Root
import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * A cursor is the sort key of the row it points at, plus the ordering it was issued under.
 *
 * The ordering is carried so a cursor from a differently sorted query can be refused. Without it the
 * page would be cut along the wrong key and come back plausible and wrong, which is the failure mode
 * cursors are supposed to remove.
 *
 * **Encoding is not encryption.** A client can read a cursor and, if it likes, forge one; what it
 * buys is a page starting somewhere else, because the values still go through the same binding as
 * every other parameter.
 */
@Serializable
private data class Cursor(
    val sort: String,
    val values: List<String>,
)

internal fun <T : Any> encodeCursor(
    row: T,
    keys: List<SortKey<T>>,
): String {
    val cursor = Cursor(fingerprintOf(keys), keys.map { asText(it.valueOf(row)) })
    return Base64.getUrlEncoder().withoutPadding().encodeToString(jpaJson.encodeToString(cursor).toByteArray())
}

/** The reverse, with the ordering checked and each value read back as the type the mapping says. */
internal fun <T : Any> decodeCursor(
    text: String,
    keys: List<SortKey<T>>,
    root: Root<T>,
): List<Comparable<*>> {
    val cursor =
        runCatching { jpaJson.decodeFromString<Cursor>(String(Base64.getUrlDecoder().decode(text))) }
            .getOrElse { throw JpaPaginationException("cursor is not one this query issued") }

    if (cursor.sort != fingerprintOf(keys)) {
        throw JpaPaginationException("cursor was issued for a different sort order")
    }
    if (cursor.values.size != keys.size) {
        throw JpaPaginationException("cursor carries ${cursor.values.size} keys and the sort has ${keys.size}")
    }

    return keys.mapIndexed { index, key ->
        fromText(root.model.getSingularAttribute(key.name).javaType, cursor.values[index])
    }
}

private fun <T : Any> fingerprintOf(keys: List<SortKey<T>>): String =
    keys.joinToString(",") { "${it.name}:${if (it.ascending) "asc" else "desc"}" }
