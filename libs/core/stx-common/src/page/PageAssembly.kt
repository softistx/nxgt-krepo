package com.softistx.common.page

/**
 * Turns the rows a keyset query returned into a [Page].
 *
 * The one row fetched beyond [limit] is the whole answer to *is there another page*, and it costs a
 * row rather than a second query — so this takes [rows] with that extra row still on, trims it, and
 * reports it as [PageInfo.hasNextPage] or [PageInfo.hasPreviousPage] depending on which way the
 * caller is walking. A backward page is read in reverse by the database and handed back the way the
 * caller reads it.
 *
 * [rows] is not the caller's element type, because neither store has one yet at this point: Mongo
 * holds raw `BsonDocument`s it decodes twice over — once into `T` and once into a cursor — and JPA
 * holds entities it has to read sort keys back off. So the cursor and the value are both taken as
 * functions of the row, and [valueOf] runs only on the rows that survive the trim.
 *
 * @param resumed whether the request carried a cursor, which is what makes the far side of a page
 *   known to exist without asking.
 */
fun <R, T> pageOf(
    rows: List<R>,
    limit: Int?,
    forward: Boolean,
    resumed: Boolean,
    cursorOf: (R) -> String,
    valueOf: (R) -> T,
): Page<T> {
    val more = limit != null && rows.size > limit
    val kept = (if (limit == null) rows else rows.take(limit)).let { if (forward) it else it.asReversed() }

    return Page(
        data = kept.map(valueOf),
        info =
            PageInfo(
                startCursor = kept.firstOrNull()?.let(cursorOf),
                endCursor = kept.lastOrNull()?.let(cursorOf),
                hasPreviousPage = if (forward) resumed else more,
                hasNextPage = if (forward) more else resumed,
            ),
    )
}
