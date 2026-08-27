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

## Lock

```kotlin
RedisLock(redis, "invoice:42").withLock(wait = 5.seconds) { chargeCard() }
```

`SET key token NX PX ttl` is the whole acquisition — one round trip, and the TTL is what makes a
holder that dies mid-task recoverable, since there is nobody left to release it. Three details do
the real work:

- **Release and extend are Lua.** `GET` then `DEL` from the client looks equivalent and is not:
  between the two, the lock can expire and be taken by someone else, and the `DEL` then frees a lock
  this caller does not hold — the one failure a lock exists to prevent. The test releases with the
  wrong token and asserts the right lock survived.
- **A watchdog puts the TTL back** every third of it while the block runs. Without it the TTL is a
  deadline on the work rather than a lock: a slow block loses it silently and a second holder starts
  the same task. `renew = false` where the work genuinely must not outlive the TTL — both halves
  have a test.
- **`withLock` polls**, because a lock has no queue to block on. The last attempt lands within one
  retry interval of `wait`; `withLockOrNull` answers null where the caller would rather skip the work
  than fail.

**What this is not**: a lock on one Redis, not Redlock across several. If that Redis fails over to a
replica that had not yet received the `SET`, two holders can believe they have it. That is fine for
keeping a scheduled job from running twice or serialising a cache rebuild, and it is not the thing
to put between two writers and a corrupted invoice — that writer needs its own conditional write.

## Topics

```kotlin
val events = RedisTopic(redis, "orders", ValueCodec.json<OrderEvent>())
events.subscribe().collect { handle(it) }
events.publish(OrderEvent.Placed(id))
```

`subscribe()` is a cold flow that opens a connection of its own — a subscribed connection speaks
only the subscribe protocol, so sharing the main one would take the rest of the application's
commands down with it — and closes it when collection ends.

It is a `callbackFlow` over Lettuce's listener API rather than its reactive `observeChannels()`,
because the listener has to be registered *before* `SUBSCRIBE` goes out. With the reactive flux, the
window between the server accepting the subscription and Reactor attaching its sink is a window
where messages are dropped.

`RedisTopicPattern` is the glob version, and is a separate class on purpose: a pattern has no
publish, since `PUBLISH orders:*` sends to a channel literally called `orders:*` that nobody is
listening to.

**Pub/sub delivers to whoever is listening right now, and forgets.** Nothing is stored, nothing is
replayed, and a subscriber that was reconnecting missed whatever went past — `publish` returning 0
is the honest signal that nobody heard it. Right for a cache invalidation or a "go and look" nudge;
wrong for anything that has to happen, which is what `RedisStream` is for.
