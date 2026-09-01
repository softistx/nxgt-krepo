package com.softistx.redis

import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Every key under this connection's namespace matching [pattern], a batch at a time.
 *
 * **[pattern] is relative to the namespace**, the same way [Redis.key]'s parts are: `scanKeys("*")`
 * on a connection namespaced `billing` walks `billing:*` and nothing else, and the keys it emits are
 * whole keys, ready for `DEL`. Every caller of this used to build that prefix by hand, which is the
 * definition of a default in the wrong place — and on the one where it was forgotten, `"*"` meant
 * every key on the server. Reaching outside the namespace on purpose is `commands.scan(...)`, which
 * is Lettuce's own and says what it is doing.
 *
 * `SCAN`, never `KEYS`. `KEYS` walks the entire keyspace in one shot with the server single-threaded
 * throughout, so on a shared instance it is a stall for everyone else — the answer is the same and
 * the cost lands on somebody who did not ask for it. `SCAN` gives no snapshot in return: a key added
 * during the walk may or may not appear, and one that survives the whole walk always does.
 */
fun Redis.scanKeys(
    pattern: String = "*",
    batch: Int = 500,
): Flow<String> =
    flow {
        var cursor: ScanCursor = ScanCursor.INITIAL
        val args = ScanArgs.Builder.matches(key(pattern)).limit(batch.toLong())
        do {
            val page = commands.scan(cursor, args) ?: break
            page.keys.forEach { emit(it) }
            cursor = page
        } while (!cursor.isFinished)
    }

/**
 * Deletes every key under this connection's namespace matching [pattern], and answers with how many.
 *
 * [pattern] is relative to the namespace — see [scanKeys], which this is the destructive half of, and
 * where the reasoning lives. Batched, so a large match does not become one enormous `DEL`.
 */
suspend fun Redis.deleteKeys(
    pattern: String = "*",
    batch: Int = 500,
): Long {
    var deleted = 0L
    var cursor: ScanCursor = ScanCursor.INITIAL
    val args = ScanArgs.Builder.matches(key(pattern)).limit(batch.toLong())
    do {
        val page = commands.scan(cursor, args) ?: break
        if (page.keys.isNotEmpty()) deleted += commands.del(*page.keys.toTypedArray()) ?: 0L
        cursor = page
    } while (!cursor.isFinished)
    return deleted
}
