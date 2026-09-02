# stx-kafka-ktor

`install(KafkaCluster)` — the bootstrap servers and the `Json` in one place, reachable from a route.

```kotlin
install(KafkaCluster) { config = KafkaConfig(bootstrap = System.getenv("KAFKA_BOOTSTRAP")) }

post("/orders") { call.kafka.publisher<OrderPlaced>().use { it.send("orders", order) } }
```

## This plugin opens nothing and closes nothing

It is the odd one out among the plugins in this repo, and it is odd for the reason `stx-kafka`
itself gives: a Kafka client **connects when it is created**, and a producer, a consumer and an
admin client have different lifetimes, different threads and different failure modes. Pretending one
object owns all three is how a wrapper ends up closing a producer something else was still using.

So the caller owns what it opens, exactly as it does without Ktor — `use { }` in the example above
is not decoration. What the plugin is worth is what it says: one place the configuration lives, and
a route that reaches it without a `Kafka` threaded through every constructor.

`instance` therefore raises no ownership question here, unlike in the other plugins: this one has
never opened anything to be confused about.

## A long-lived publisher is not a request-scoped one

`call.kafka.publisher<T>()` inside a handler opens a producer per request, which is right for a
one-off and wrong for a hot path. A publisher or a subscriber that belongs to the application should
be opened once at startup and closed on `ApplicationStopped` — the way the other plugins here treat
their connections, done by hand because this plugin deliberately does not do it for you.

## Why this is a module and not a package in `stx-ktor`

It used to be `com.softistx.ktor.kafka`, one of seven integrations in one artifact. Publishing it
beside its library is what the other families here already do, and it lets the `stx-kafka`
dependency be `exported` instead of `compile-only`.

`own`, `publish`, `resource` and `required` stay in `stx-ktor`: they belong to no integration.
