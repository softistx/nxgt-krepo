# stx-amqp

AMQP for a Kotlin coroutine service, over the
[RabbitMQ Java client](https://www.rabbitmq.com/client-libraries/java-api-guide) — which is a
blocking, callback-driven API with a thread pool of its own. This module is the coroutine shape over
it: a publish that suspends until the broker confirms it, deliveries that arrive as a `Flow`,
acknowledgements that happen after the handler returned, and a retry path that is a real one.

**`io.github.softistx:stx-amqp`** — [how to depend on it](../../../docs/consuming.md).

It is a plain `jvm/lib`. Nothing here knows about a server framework, so the same code serves a Ktor
route, a background worker or a CLI.

## Connecting

```kotlin
Amqp.connect(AmqpConfig(uri = "amqp://rabbit:5672/billing")).use { amqp ->
    amqp.publisher<OrderEvent>("orders").use { orders ->
        orders.publish(OrderPlaced(id), routingKey = "order.placed")
    }
}
```

**One connection, many channels.** AMQP multiplexes: a connection is one socket carrying any number
of channels, and the reason to open a second one is a second set of credentials or a second virtual
host — never a second thread. A `Channel`, on the other hand, is not thread-safe and its delivery
tags mean nothing anywhere else, so every publisher and consumer here opens its own, and one-shot
work like declaring topology borrows one for the call.

**The virtual host is where the URI goes wrong.** The default vhost is *named* `/`, and a URI path
of `/` means the **empty** vhost. `amqp://host:5672/` therefore authenticates against a vhost most
brokers do not have and fails with a message about permissions. Either leave the path off entirely
or escape it as `amqp://host:5672/%2F`.

## Shape

```
com.softistx.amqp            the connection and its configuration — Amqp, AmqpConfig, channels
com.softistx.amqp.codec      the Json every body serializes through, and the codec seam under it
com.softistx.amqp.message    AmqpMessage<T> and its headers — what a consumer hands a handler
com.softistx.amqp.topology   exchanges, queues, bindings, and the operations a queue is asked about
com.softistx.amqp.publisher  AmqpPublisher<T> — publish, publishAll, and what a confirm means
com.softistx.amqp.consumer   AmqpConsumer<T> — deliveries as a Flow, or process with acknowledgements
com.softistx.amqp.retry      RetryQueue<T> — delay queues, an attempt count, and a parking queue
```

## Serialization

Bodies are kotlinx.serialization first: a `@Serializable` type needs no plumbing, and the connection
owns the `Json` every typed client uses.

```kotlin
val amqp = Amqp.connect(AmqpConfig(uri))

amqp.publisher<OrderEvent>("orders")
amqp.consumer<OrderEvent>("billing")
```

That `Json` is configured once, on `AmqpConfig`, rather than once per publisher and consumer. The
default (`amqpJson`) ignores unknown keys, because a message is written by whoever deployed last and
read by whoever deployed first: a consumer that throws on a field a newer publisher added is a
consumer that empties into its dead-letter queue for as long as a rolling deploy takes.

A body that is not JSON goes through an `AmqpCodec` instead — `AmqpCodec.text`, `.bytes`, or one of
your own. The content type travels with the message, so a consumer in another language knows what it
was handed.

## Declaring topology

```kotlin
amqp.declare {
    exchange("orders")
    exchange("orders.dead", ExchangeType.Fanout)

    queue("billing") {
        deadLetterTo("orders.dead")
        bindTo("orders", "order.placed", "order.cancelled")
    }
    queue("billing.dead") { bindTo("orders.dead") }
}
```

Order in the block does not matter: every exchange is declared, then every queue, then every
binding, because a binding needs both ends. All of it goes over one borrowed channel.

**Declaring is idempotent, and only while nothing changed.** Re-declaring what is already there is
success and is exactly what an application should do on every start. Declaring something that
*disagrees* with what is there fails and closes the channel — so changing a queue's type or TTL
means deleting it, deliberately, by someone who knows what is in it. A declaration is not a schema
migration.

Alongside the declarations are the questions a queue gets asked in production: `messageCount`,
`consumerCount`, `queueExists`, `purgeQueue`, `deleteQueue`. `queueExists` borrows its own channel
on purpose — a *failed* passive declare closes the channel it was asked on, so asking on a shared
one would take the caller's publisher down with it.

## Publishing

```kotlin
amqp.publisher<OrderEvent>("orders").use { orders ->
    orders.publish(OrderPlaced(id), routingKey = "order.placed")
}
```

`publish` suspends until the broker has confirmed the message and answers with the sequence number
it was confirmed on. The client's own `basicPublish` returns as soon as the bytes reach the socket;
awaiting the confirm is what turns *sent* into *stored*, and a `nack` becomes an exception rather
than a success nobody looked at.

**A message that matches no binding is discarded and confirmed.** Routed nowhere, reported as
success — the most common way a working publisher delivers nothing. `PublisherOptions(mandatory =
true)` turns that into an `AmqpUnroutableException` on the publish that caused it, correlated by
message id (one is generated if you did not set one).

`persistent` is on by default and is half of durability; the other half is the queue, and a
persistent message in a queue declared non-durable still dies with the broker.

Publishing to the default exchange (`""`) with a queue's name as the routing key delivers straight
to that queue — the one case where a routing key is a destination rather than a subject.

## Consuming

```kotlin
amqp.consumer<OrderEvent>("billing").use { billing ->
    billing.process { message -> charge(message.body) }
}
```

`process` acknowledges after the handler returns — at least once, so handlers have to be idempotent.
`messages()` is the same deliveries as a `Flow` with nothing acknowledged for you, and `ack` / `nack`
are how a collector says so.

**Prefetch is the only backpressure AMQP has.** The broker hands over as many messages as it is
allowed to have unacknowledged, and the protocol's default is all of them — one consumer holding a
queue's worth of messages in memory while every other consumer of that queue holds none.
`ConsumerOptions.prefetch` is what stops that, and it defaults to 32.

**A failure is dead-lettered, not retried in place.** A requeued failure is redelivered immediately,
fails again, and is requeued again: an infinite loop that reads as a busy consumer. A rejected
message goes to the queue's dead-letter exchange instead — so declare one, because a queue without
one discards what it rejects. A body that will not decode is treated the same way, since it will not
decode on the next attempt either.

## Retrying properly

```kotlin
amqp.retryQueue<OrderEvent>("billing", RetryPolicy.Default).use { retries ->
    amqp.consumer<OrderEvent>("billing").use { billing ->
        billing.processWithRetry(retries) { message -> charge(message.body) }
    }
}
```

AMQP has no delayed delivery, so a delay is a queue nobody consumes: a failed message is republished
to `billing.retry.0`, sits there for that queue's TTL, and the broker dead-letters it back to
`billing`. One queue per delay rather than one queue with mixed TTLs — a TTL expires only the
message at the *head*, so a short delay behind a long one waits for the long one.

The attempt count travels in `x-attempt`, because what comes back is a copy. What has failed every
attempt lands in `billing.parked` carrying where it came from and the *type* of the failure — never
its message, which is where a body ends up quoted in a log. Nothing takes it from there; that is
what the name is for.

`RetryPolicy` is the list of delays, so both questions an incident asks are read off it: how many
attempts (`attempts`) and how long until it gives up (`window`).

## Tests

The specs that need no broker cover the parts that are wrong in Kotlin rather than in the broker:
the `x-` arguments a queue declaration turns into, the headers whose strings are `LongString`s, the
codec, and the retry delays.

Everything else runs against a real broker, because the behaviour under test *is* the broker's — an
acknowledgement removing a message, a rejection moving it, a TTL expiring one. They take
`AMQP_TEST_URI`, which has **no default** and makes them skip when it is unset: the URI carries the
credentials, and a credential with a default is a credential in source control. They create only the
exchanges and queues they delete, under names unique per run. See the local services section of
[AGENTS.md](../../../AGENTS.md) for the broker this workspace runs and the line that points the specs
at it.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
