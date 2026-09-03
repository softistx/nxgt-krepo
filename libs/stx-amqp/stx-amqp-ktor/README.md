# stx-amqp-ktor

`install(AmqpConnection)` — one AMQP connection for the application, closed when it stops.

```kotlin
install(AmqpConnection) { config = AmqpConfig(uri = System.getenv("AMQP_URI"), connectionName = "orders-api") }

post("/orders") { call.amqp.publisher<OrderPlaced>("orders").use { it.publish(order) } }
```

## A connection multiplexes; a channel does not

That one sentence decides the whole shape. The plugin owns the connection and nothing else —
channels, publishers and consumers are opened by whoever needs one and closed by them, through
`Amqp.openChannel` or `withChannel`. `call.amqp` is therefore shared and safe to be; the channel a
route opens over it belongs to that route.

Worth setting `connectionName`: it is what the broker's management UI shows, and `orders-api` beats
an anonymous connection when something has to be traced back to a service.

## Why the connect blocks

`Amqp.connect` suspends and plugin installation does not, so this is where `runBlocking` is called —
at startup, on the thread starting the application, before anything is serving. The alternative is a
server that accepts requests while its broker connection is still being made, and answers the first
of them with a failure that looks like the broker's fault. `stx-jpa-ktor` makes the same call for
the same reason.

## Injection

Installing the plugin registers the connection with Ktor's DI — the one it already opened, rather
than letting the container open a second. It is not a flag.
The container then closes it as well, which is harmless — `Amqp` closes through `CloseGuard` — but a
connection that has to outlive the application belongs in `instance`, which this plugin adopts and
does not close.

## Why this is a module and not a package in `stx-ktor`

It used to be `com.softistx.ktor.amqp`, one of seven integrations in one artifact. Publishing it
beside its library is what the other families here already do, and it lets the `stx-amqp` dependency
be `exported` instead of `compile-only`.

`own`, `publish`, `resource` and `required` stay in `stx-ktor`: they belong to no integration.
