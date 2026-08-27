# shared-redis

Redis for a Kotlin coroutine service, over [Lettuce](https://lettuce.io) — which already has a
suspending command interface for the whole protocol, so this module is not another wrapper around
`GET` and `SET`. It is the four things every service builds on top of one: a typed cache, a lock,
a topic, and a stream consumer.

It is a plain `jvm/lib`. Nothing here knows about a server framework, so the same code serves a Ktor
route, a background worker or a CLI.

## Getting a connection

```kotlin
val redis = Redis.connect(RedisConfig(uri = "redis://localhost:6379/0", namespace = "billing"))

redis.commands.get("some:key")           // Lettuce's own suspending API, unwrapped
redis.key("cache", "user", "42")         // billing:cache:user:42
```

**The namespace is the point of `RedisConfig`.** Redis has no schemas — one instance is one flat
keyspace, usually shared by every service that was pointed at it — so every key this module writes is
prefixed, and two applications, or an application and its own tests, can share an instance without
being able to delete each other's keys by accident. The database index belongs in the URI, where
Lettuce reads it from.

One connection is the right number: Lettuce multiplexes commands over it and is thread-safe, so a
pool buys nothing until something *blocks* it. `pubSub()` and `dedicated()` are the two cases that
do, and both hand back a connection the caller owns and closes.

## Shape

```
com.strange.redis          the connection and its lifecycle — Redis, RedisConfig, commands
com.strange.redis.codec    the Json every layer serializes through, and the escape hatch under it
com.strange.redis.cache    RedisCache<T> — get, put with a TTL, getOrLoad, invalidate
com.strange.redis.lock     RedisLock — a lock only its holder can release
com.strange.redis.pubsub   RedisTopic<T> — publish, and subscribe as a Flow
com.strange.redis.stream   RedisStream<T> — append, and consume a group as a Flow
```

Lettuce's own `RedisCoroutinesCommands` stays reachable for everything these four do not cover:
`redis.commands` is the full surface, RediSearch and vector sets included.

## Serialization

Values are kotlinx.serialization first: a `@Serializable` type needs no plumbing, and the connection
owns the `Json` every typed layer uses.

```kotlin
val redis = Redis.connect(RedisConfig(uri, namespace = "billing"))

val sessions = redis.cache<Session>("sessions", ttl = 30.minutes)
val events   = redis.topic<OrderEvent>("orders")
val orders   = redis.stream<OrderEvent>("orders", maxLength = 100_000)
```

That `Json` is configured once, on `RedisConfig`, rather than once per cache, topic and stream — the
alternative is a service whose Redis values are configured in as many places as they are
constructed, with nothing making them agree. The default (`redisJson`) is lenient about unknown
keys, because a Redis value is a copy and not the record: one written by the previous deploy,
carrying a field this version has since dropped, should still read, or a rolling deploy becomes a
fleet half of which cannot decode the other half's entries. Pass a strict `Json` in `RedisConfig`
when that skew should be loud instead — the read then fails as `RedisValueException` naming the key.

Two ways past the reified factories, both narrow:

- `RedisCache(redis, "sessions", Session.serializer())` — the same thing named by its serializer,
  for a call site whose `T` cannot be reified. Every layer has this constructor.
- `RedisCache(redis, "flags", ValueCodec.string)` — for a value that must **not** be JSON, because
  something else reads the same key: a flag that should be `on` and not `"on"`, or a counter that has
  to stay a number `INCR` can touch. `ValueCodec` is that seam, and it is the exception.

## Cache

```kotlin
val sessions = redis.cache<Session>("sessions", ttl = 30.minutes)
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
  and it belongs in the type — `redis.cache<Session?>("sessions")` — not in a special case here.
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
val events = redis.topic<OrderEvent>("orders")
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

## Streams

The durable half. Where a topic delivers to whoever is listening and forgets, a stream keeps every
entry until it is trimmed, hands each one to exactly one consumer in a group, and remembers the
hand-over until somebody acknowledges it.

```kotlin
val orders = redis.stream<OrderEvent>("orders", maxLength = 100_000)
orders.append(OrderEvent.Placed(id))
orders.process(group = "billing", consumer = "worker-1") { event -> charge(event) }
```

- **Delivery is at least once.** `process` acknowledges *after* the handler returns, so a handler
  that succeeds and then loses the connection sees its entry again — handlers have to be idempotent.
  Acknowledging first would trade that for losing the entry, which is the worse half of the same
  coin. A handler that throws does not acknowledge and the exception reaches the caller, because a
  consumer that swallows failures is a queue that has quietly stopped working.
- **`consume` opens its own connection.** `XREADGROUP BLOCK` holds one, and Lettuce multiplexes
  everything else over the shared connection, so a blocking read there would stall the application
  for the length of the block.
- **`block` is a poll interval, not a timeout.** The loop comes round and re-checks whether anyone is
  still collecting. Blocking forever saves a round trip and buys a consumer that ignores cancellation
  until the next entry arrives — on a quiet stream, a very long time.
- **A group starts at the end of the stream.** It is a subscription, not a backfill; `history()` is
  how to read what came before.
- **`claimStale` is the recovery path**, and there is no free one: an entry handed to a worker that
  then died stays pending forever, because the group has already delivered it. Run it on an interval
  with a `minIdle` comfortably longer than a normal handler takes.
