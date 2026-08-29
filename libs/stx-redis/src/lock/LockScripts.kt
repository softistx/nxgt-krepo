package com.strange.redis.lock

/**
 * Release and extend, as Lua, because both are read-then-write and Redis only guarantees a script
 * runs alone.
 *
 * `GET` followed by `DEL` from the client looks equivalent and is not: between the two, the lock can
 * expire and be taken by somebody else, and the `DEL` then frees a lock this caller does not hold —
 * which is the one failure a lock exists to prevent. Comparing the token inside the script is what
 * makes "release" mean "release *mine*".
 */
internal const val RELEASE_IF_HELD = """
    if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('DEL', KEYS[1])
    end
    return 0
"""

internal const val EXTEND_IF_HELD = """
    if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('PEXPIRE', KEYS[1], ARGV[2])
    end
    return 0
"""
