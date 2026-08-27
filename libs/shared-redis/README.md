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
