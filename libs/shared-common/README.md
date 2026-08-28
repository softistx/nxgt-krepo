# shared-common

What more than one module here needs, and nothing else.

It is a plain `jvm/lib` depending on kotlinx-coroutines and kotlinx-serialization and on nothing
else — no broker, no database, no wire format. That constraint is the module's whole design: the
moment something in here knows what a topic or a collection is, every library that depends on it
inherits that knowledge, and a shared module that depends on everything is a dependency cycle
waiting for its second commit.

## Shape

```
com.strange.common.coroutines      CoroutineSafeMap, KeyedMutex, Mailbox
com.strange.common.lifecycle       CloseGuard
com.strange.common.serialization   lenientJson, decodeValue, typeName
com.strange.common.page            Page, PageInfo, PageWindow and pageOf — the half of keyset
                                   pagination that is the same in both stores
```

## Closing once

`CloseGuard` runs a close the first time and does nothing on every call after it.

```kotlin
class Redis internal constructor(…) : AutoCloseable {
    private val guard = CloseGuard()

    override fun close() = guard.once { connection.close(); client.shutdown() }
}
```

**A resource that is handed around is closed more than once.** Ktor's DI closes every
`AutoCloseable` it hands out when the application stops — one a provider merely passed through
included — and a plugin, a container and the code that built the thing all have a reasonable claim
to closing it. Ownership rules say who *should*; this says what happens when two of them do, which
is nothing.

The clients underneath do not agree on this by themselves: Lettuce and the MinIO client tolerate a
second close, the RabbitMQ client throws `AlreadyClosedException`, and none of them owes us that
behaviour in the next version. The guard makes one contract out of three.

It holds an `AtomicBoolean` rather than a `Mutex`, which is the same question the three concurrency
types below answer: `close()` is an ordinary blocking function called from `use` blocks, shutdown
hooks and container teardown, none of which is a coroutine.

## Which of the three concurrency types

They look interchangeable and are not. The question that separates them is **who is calling**.

```kotlin
// Every caller is a coroutine, and each operation stands alone.
private val sessions = CoroutineSafeMap<UserId, Session>()

// Every caller is a coroutine, and the work behind a key suspends.
private val loading = KeyedMutex<UserId>()

// Some callers are not coroutines at all — a Java listener, a driver's callback.
private val confirms = Mailbox<Confirm>()
```

**`CoroutineSafeMap`** is a `Mutex` and a map. A mutex suspends where a `synchronized` block parks a
thread, which is the right trade when every caller is a coroutine.

Its `getOrPut` takes a **value, not a loader**. A suspending default would run while the lock is
held, so one slow load would block every other key — and a signature that cannot express the
mistake is better than a KDoc warning about it. Each call is atomic on its own; `update` is there
for the read-then-write that is only correct as one step.

**`KeyedMutex`** is the case `getOrPut` refuses: one lock per key, so callers wanting different
things stay concurrent and only the ones about to do the same work twice wait. It is what a cold
cache wants the moment everybody asks for the same popular key at once.

```kotlin
suspend fun profile(id: UserId): Profile =
    cache.get(id) ?: loading.withLock(id) {
        cache.get(id) ?: fetch(id).also { cache.put(id, it) }   // look again: someone was ahead of you
    }
```

**`Mailbox`** is the way in from a thread that cannot suspend, and there is no other correct one. A
`Mutex` cannot be taken from a Java callback, because taking it may suspend; `runBlocking` around it
parks a thread the library needs for its own I/O, which is how a client deadlocks against itself.
`post` is `trySend` on an unbounded channel: never blocks, never waits.

What it buys is bigger than thread safety. One consumer means the state behind the messages has a
single owner and needs no synchronisation at all — plain `var`s, plain maps, no `@Volatile`. And a
channel is FIFO, so two messages that must be applied in order *are*, which two guarded flags cannot
promise however carefully each one is guarded. `AmqpPublisher` settles the broker's confirms this
way, and `KafkaSubscriber` carries its handlers' completions the same way.

## Serialization

`lenientJson` is the one `Json { ignoreUnknownKeys = true }` that `redisJson`, `kafkaJson` and
`amqpJson` all are. Lenient because of who wrote the value: a stored value or a message is produced
by whoever deployed last and read by whoever deployed first, and a reader that throws on a field a
newer writer added is a reader that stops during every rolling deploy, on data that was perfectly
good. Strictness belongs on the way *in*, where the writer can still be fixed.

`decodeValue` is the three lines every typed layer over a wire format repeats around a
`SerializationException`, with the part that differs — the exception, and the context in it — left
to the caller:

```kotlin
json.decodeValue(serializer, raw) { failure ->
    RedisValueException("stored value is not a ${serializer.typeName}", failure)
}
```

It is deliberately **not** given the text to pass on. A stored value is somebody's payment details
as often as it is a test fixture, and an exception message ends up in a log nobody meant to make
sensitive. `typeName` is what a caller needs and all it needs.

What is *not* here is a shared codec interface. `ValueCodec` in shared-redis is `String` to `T`
because a Redis value should be readable by `redis-cli`; `KafkaSerde` has to expose Kafka's own
`Serializer` and `Deserializer`; `AmqpCodec` carries a content type. They look alike from a distance
and are three different things up close.

## One page, whichever store it came from

`Page<T>` is a list and a Relay-shaped `PageInfo` — `startCursor`, `endCursor`, `hasNextPage`,
`hasPreviousPage`. `shared-mongo`'s `findPage` and `shared-jpa`'s both answer with it, and that is
the whole reason it is here: a route that pages over either store maps the data and leaves the
cursors alone, with `Page.map`, and does not care which one it was.

`PageWindow` is the *request* side of the same story: `first`, `last`, `cursor`, the `limit` and
`forward` derived from them, and the two rules — not both ends, and not a page of zero rows. A
store's request type implements it and keeps whatever it needs beyond that: Mongo's
`PaginationOptions` carries a filter and a sort as raw Mongo JSON, because that is how they arrive
from an HTTP client, and is `@Serializable`.

`check` takes the exception rather than throwing its own, and that is deliberate: each store has a
sealed exception family that a caller catches in one clause, and a request refused by a rule living
here still has to land in that clause. `init { check(::InvalidPaginationException) }` is the whole
integration.

`pageOf` is the *answer* side: given the rows a query for `limit + 1` returned, it trims the extra
one, reverses a backward page into reading order, and turns "did the extra row turn up" and "did the
caller resume" into a `PageInfo`. It takes the cursor and the value as functions of a row because a
store does not have the caller's type in hand yet — Mongo holds raw `BsonDocument`s it decodes twice
over. `libs/shared-common/test/page/PagingTest.kt` pins all of it, and needs no database.

**`PageWindow` and `pageOf` have one caller, and `Page` has two.** `shared-jpa` used to be the
second: its keyset paging needed a query object it could rebuild per page and read the sort keys back
off, which is a layer above a criteria rather than a part of one, and it went with the query DSL.
What that module pages with now is `limit` and `offset`, which needs neither a window type nor an
assembler — it answers with a `Page` whose cursors are null, because there are none. The two stay
because they are the shape any second keyset store would want, and because deleting a correct,
database-free, fully specified implementation to save two files is not a trade worth making.

## What belongs here

Something a **second** module needs, expressed without knowing anything about the first. A helper
used once belongs next to its only caller, where it can be read alongside the code that explains it;
it moves here when a second caller appears, not in anticipation of one.

Something that would otherwise be **written twice and drift** — the leniency argument above was
written out three times before it was written down once.

Nothing that names a broker, a database, a wire format or a framework. If a type here would need
`import org.apache.kafka`, it belongs in `shared-kafka`.
