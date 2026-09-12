package com.softistx.workflow.redis

/**
 * The record and the index move together or not at all.
 *
 * A `SET` that landed without its `ZADD` would leave an instance nothing polls for — invisible until
 * somebody resumed it by hand — and a `ZADD` without its `SET` would send a worker after a record
 * that had not changed. Lua is what makes the pair atomic; `MULTI` would too, but only on a
 * connection of its own, because it blocks the multiplexed one every other command shares.
 */
internal object StoreScripts {
    /**
     * Writes an instance that must not exist yet. 1 if it was written, 0 if the id was taken.
     *
     * `ARGV[3]` empty means the same thing it means in [SAVE] — nothing is due — and it is empty for
     * the same three cases. An engine only ever creates a running instance, but the rule about what
     * belongs in the index is the store's, not the engine's, and a store whose two write paths
     * disagreed about it would be a bug waiting for the first caller who created one that was not.
     */
    val CREATE =
        """
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        redis.call('HSET', KEYS[1], 'record', ARGV[1], 'version', ARGV[2], 'status', ARGV[5])
        if ARGV[3] ~= '' then redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4]) end
        redis.call('ZADD', ARGV[6] .. ARGV[5], ARGV[7], ARGV[4])
        return 1
        """.trimIndent()

    /**
     * Writes an instance if its stored version is still the expected one, and re-schedules it.
     *
     * `ARGV[4]` empty means the instance is finished: it leaves the due-time index, and takes the
     * retention TTL in `ARGV[6]` if one was configured.
     *
     * The status index moves with it. The old status comes from the hash rather than from the
     * caller, because the caller has the record it is *writing* and not the one that is stored —
     * and an id left in the set of a status it no longer has is an operator's inbox showing work
     * that is already done.
     */
    val SAVE =
        """
        local stored = redis.call('HGET', KEYS[1], 'version')
        if not stored or tonumber(stored) ~= tonumber(ARGV[2]) then return 0 end
        local was = redis.call('HGET', KEYS[1], 'status')
        if was and was ~= ARGV[7] then redis.call('ZREM', ARGV[8] .. was, ARGV[5]) end
        redis.call('ZADD', ARGV[8] .. ARGV[7], ARGV[9], ARGV[5])
        redis.call('HSET', KEYS[1], 'record', ARGV[1], 'version', ARGV[3], 'status', ARGV[7])
        if ARGV[4] == '' then
          redis.call('ZREM', KEYS[2], ARGV[5])
          if ARGV[6] ~= '' then redis.call('PEXPIRE', KEYS[1], ARGV[6]) end
        else
          redis.call('ZADD', KEYS[2], ARGV[4], ARGV[5])
          redis.call('PERSIST', KEYS[1])
        end
        return 1
        """.trimIndent()
}
