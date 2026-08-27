# shared-kafka

Kafka for a Kotlin coroutine service, over the
[Apache Kafka clients](https://kafka.apache.org/documentation/#api) — which are a blocking,
callback-and-`Future` API written for a thread per consumer. This module is the coroutine shape over
them: a send that suspends until the broker acknowledges it, records that arrive as a `Flow`, and
offsets that are committed after the handler returned rather than on a timer.

It is a plain `jvm/lib`. Nothing here knows about a server framework, so the same code serves a Ktor
route, a background worker or a CLI.

## Getting a cluster

```kotlin
val kafka = Kafka(KafkaConfig(bootstrap = "kafka1:9092,kafka2:9094,kafka3:9096"))
```

**`Kafka` opens nothing.** There is no `connect()`, because a Kafka client connects when it is
constructed: this is the configuration and the factories over it, and the connections belong to the
publisher, subscriber and admin client it hands out — each owned and closed by whoever asked for it.
A producer and a consumer have different lifetimes, different failure modes and different reasons to
be closed, and a wrapper that owns them all is a wrapper that eventually closes a producer somebody
else was still using.

`bootstrap` is a seed and not the cluster: the client asks whoever answers for the real broker list
and talks to those from then on. Listing more than one is how a start survives a broker being down.

## Shape

```
com.strange.kafka           the cluster and its configuration — Kafka, KafkaConfig, properties
com.strange.kafka.serde     the Json every typed client serializes through, and the serde under it
com.strange.kafka.record    KafkaRecord<K, V> and its headers — what a subscriber hands a handler
com.strange.kafka.producer  KafkaPublisher<K, V> — send, sendAll, and the options that shape them
com.strange.kafka.consumer  KafkaSubscriber<K, V> — records as a Flow, or process with commits
com.strange.kafka.admin     KafkaAdmin — topics, partitions, consumer groups and their lag
```

## Serialization

Values are kotlinx.serialization first: a `@Serializable` type needs no plumbing, and the cluster
owns the `Json` every typed client uses.

```kotlin
val kafka = Kafka(KafkaConfig(bootstrap))

kafka.publisher<OrderEvent>()                            // String keys, JSON values
kafka.subscriber<OrderEvent>("billing", listOf("orders"))
```

That `Json` is configured once, on `KafkaConfig`, rather than once per publisher and subscriber —
the alternative is a service whose Kafka values are configured in as many places as they are
constructed, with nothing making them agree. The default (`kafkaJson`) ignores unknown keys, because
a record is written by whoever deployed last and read by whoever deployed first: a consumer that
throws on a field a newer producer added is a consumer that stops on a rolling deploy.

A value that is not JSON goes through a `KafkaSerde` instead — `KafkaSerde.string`, `.long`,
`.bytes`, or `KafkaSerde.of(serializer, deserializer)` for anything else. The typed factories hold
one of these underneath, so nothing is hidden behind the reified overloads.

## Publishing

```kotlin
kafka.publisher<OrderEvent>().use { orders ->
    val sent = orders.send("orders", OrderPlaced(id), key = id)
    sent.partition // where it landed
}
```

`send` suspends until the broker has acknowledged the record on the terms `PublisherOptions.acks`
asked for, and answers with where it landed. Kafka's own `send` returns a `Future` and takes a
callback; awaiting the callback is what turns *fired* into *stored*. A caller that does not want to
wait launches sends concurrently — the producer batches them either way.

**The key decides the order, not the call.** Records sharing a key land on one partition and are
read in the order they were written; a null key spreads them across partitions and gives that up.
`sendAll` therefore hands the whole collection to the producer in order and then awaits the
acknowledgements together, rather than racing one coroutine per record — the batching is the same
and the ordering survives.

`PublisherOptions` models the settings worth an opinion — `acks`, `idempotent`, `linger`,
`compression`, `maxBlock` — and `properties` takes anything else, spelled the way Kafka spells it, so a line from
Kafka's own documentation can be pasted in and work.

## Subscribing

```kotlin
kafka.subscriber<OrderEvent>("billing", listOf("orders")).use { orders ->
    orders.process { record -> charge(record.value) }
}
```

`process` commits on the terms `SubscriberOptions.commit` set — `Batched` by default, `AfterEach`
for the least redelivery, `Manual` for a caller that wants to say so itself. `records()` is the same
loop as a `Flow` with nothing committed for you, and `commit(record)` / `commitPending()` are how a
collector says a record is done.

**Delivery is at least once.** Every commit happens after the handler returned, so a handler that
succeeds and then loses the connection sees its record again. Handlers have to be idempotent. The
alternative — committing first — trades duplicates for records that are silently never processed,
which is the worse half of the same coin.

Four things about the loop are worth knowing, because they are what a thin wrapper gets wrong:

**A slow handler never stops the polling.** When the buffer between the loop and the handler fills,
the loop pauses its partitions and *keeps calling* `poll`, which then returns nothing but goes on
proving this consumer is alive. Simply not polling while the handler works is what gets a consumer
evicted mid-batch and its records handed to somebody else — the failure that looks like duplicate
processing under load and is really a rebalance storm.

**Concurrency is per partition, and the offsets know it.** `Concurrency.PerPartition` runs the
partitions alongside each other while each keeps its own order. Handlers then finish out of order —
5, then 7, while 6 is still running — so what gets committed is the contiguous completed prefix of
each partition. Committing 8 there would mean a crash loses record 6 for good.

**A rebalance is where offsets are lost.** Partitions are committed on revocation, before they are
handed over, and forgotten afterwards. Partitions that are *lost* rather than revoked are only
forgotten: committing them would be claiming work this consumer no longer owns.

**The client wants exclusion, not a thread.** `KafkaConsumer` takes an owner slot on the way into
every method and frees it on the way out, so consecutive calls from different threads are fine and
only overlapping ones throw — which `ConsumerConfinementTest` establishes against the real client
rather than asserting from memory. So the loop runs on `Dispatchers.IO.limitedParallelism(1)` and
owns the client outright; handlers never touch that dispatcher, because a poll holds it for the
whole poll timeout. A completion or a commit is a message the loop applies on its next turn, which
is also what puts a completion and the commit that must follow it in one order instead of two.

`assignment` is a `StateFlow` of the partitions this consumer owns, so a rebalance is something to
react to rather than something to poll for.

## Administering

```kotlin
kafka.admin().use { admin ->
    admin.ensureTopic("orders", partitions = 6, replication = 3)
    admin.lag("billing")   // per partition, how far behind the group is
}
```

`ensureTopic` is idempotent — a topic that already exists is success, not `TopicExistsException` —
which is what makes it safe to call on every start. `describe` reports the in-sync replicas, and
`lag` is the number every alert is actually about: committed offset against end offset, per
partition.

## Tests

The unit specs use Kafka's own `MockProducer` and `MockConsumer` and need nothing running: they are
what make the *timing* claims checkable — that a full buffer pauses the partitions, that a strategy
commits when it says it does, that partitions run concurrently while each keeps its order.

The integration specs point at a real cluster through `KAFKA_TEST_BOOTSTRAP` and skip themselves
when it is unreachable, so a machine without one reports skipped tests rather than a red build. They
create only the topics and groups they delete and never touch one they did not create — see the
local services section of [AGENTS.md](../../AGENTS.md) for the cluster this workspace runs and the
`/etc/hosts` entries its advertised listeners need.
