---
"stx-common": patch
"stx-redis": patch
---

A lease or lock renewal that throws is retried instead of cancelling the guarded block, a cancelled `KeyedMutex` caller or `RedisLock` holder still gives its place back, `RedisStream.history(count)` returns the last entries, and `RedisCache.putAll` writes as one batch.
