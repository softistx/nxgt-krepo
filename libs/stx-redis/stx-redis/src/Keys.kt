package com.softistx.redis

/**
 * `namespace:part:part`, skipping the empty ones.
 *
 * Colons are Redis convention, not syntax: nothing enforces them, and every tool that shows a
 * keyspace as a tree reads them. Dropping empty parts is what makes the convention composable — a
 * layer can pass a namespace it may not have without producing `:cache:user`, and every caller
 * spells the separator in exactly one place.
 */
fun redisKey(
    namespace: String,
    vararg parts: String,
): String = (listOf(namespace) + parts).filter { it.isNotEmpty() }.joinToString(":")
