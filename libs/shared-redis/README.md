# shared-redis

Redis for a Kotlin coroutine service, over [Lettuce](https://lettuce.io) — which already has a
suspending command interface for the whole protocol, so this module is not another wrapper around
`GET` and `SET`. It is the four things every service builds on top of one: a typed cache, a lock,
a topic, and a stream consumer.

It is a plain `jvm/lib`. Nothing here knows about a server framework, so the same code serves a Ktor
route, a background worker or a CLI.

## Shape

```
com.strange.redis          the connection and its lifecycle — Redis, RedisConfig, commands
com.strange.redis.codec    values as their kotlinx.serialization form, keys as strings
com.strange.redis.cache    RedisCache<T> — get, put with a TTL, getOrLoad, invalidate
com.strange.redis.lock     RedisLock — a lock only its holder can release
com.strange.redis.pubsub   RedisTopic<T> — publish, and subscribe as a Flow
com.strange.redis.stream   RedisStream<T> — append, and consume a group as a Flow
```

Lettuce's own `RedisCoroutinesCommands` stays reachable for everything these four do not cover:
`redis.commands` is the full surface, RediSearch and vector sets included.

## Cache

```kotlin
val sessions = RedisCache(redis, "sessions", ValueCodec.json<Session>(), ttl = 30.minutes)
val session = sessions.getOrLoad(id) { database.loadSession(id) }
```

`get`, `getAll` (one `MGET`), `put`, `putAll`, `getOrLoad`, `contains`, `expiresIn`, `invalidate`,
`invalidateAll`. Four things it deliberately does *not* do:

- **`getOrLoad` is not single-flight.** Ten requests missing the same key call the loader ten times.
  Making it single-flight means a lock on every miss — two round trips on the path that is supposed
  to be the fast one, and a cache that has become a coordination point. When a load is expensive
  enough for the stampede to matter, wrap it in a `RedisLock` at the call site, where the cost is a
  decision instead of a default.
- **A null is an absence, not a cached value.** Caching "this does not exist" is a real technique,
  and it belongs in the codec — `RedisCache<Session?>` — rather than in a special case here.
- **`putAll` is not `MSET`.** `MSET` cannot carry a TTL, so entries written with one would live
  forever. The writes go out concurrently instead, which Lettuce multiplexes into a pipeline.
- **`invalidateAll` scans, it does not `KEYS`.** `KEYS` walks the whole keyspace with the server
  single-threaded throughout; on a shared instance that is a stall charged to everyone else.
