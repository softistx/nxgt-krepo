package com.strange.workflow.redis

/**
 * The record and the index move together or not at all.
 *
 * A `SET` that landed without its `ZADD` would leave an instance nothing polls for — invisible until
 * somebody resumed it by hand — and a `ZADD` without its `SET` would send a worker after a record
 * that had not changed. Lua is what makes the pair atomic; `MULTI` would too, but only on a
 * connection of its own, because it blocks the multiplexed one every other command shares.
 */
internal object StoreScripts {
    /** Writes an instance that must not exist yet. 1 if it was written, 0 if the id was taken. */
    val CREATE =
        """
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        redis.call('HSET', KEYS[1], 'record', ARGV[1], 'version', ARGV[2])
        redis.call('ZADD', KEYS[2], ARGV[3], ARGV[4])
        return 1
        """.trimIndent()

    /**
     * Writes an instance if its stored version is still the expected one, and re-schedules it.
     *
     * `ARGV[4]` empty means the instance is finished: it leaves the index, and takes the retention
     * TTL in `ARGV[6]` if one was configured.
     */
    val SAVE =
        """
        local stored = redis.call('HGET', KEYS[1], 'version')
        if not stored or tonumber(stored) ~= tonumber(ARGV[2]) then return 0 end
        redis.call('HSET', KEYS[1], 'record', ARGV[1], 'version', ARGV[3])
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
