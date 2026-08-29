# stx-koin

Koin modules for the libraries here — one package per integration, one module for all of them.

```
com.strange.koin.i18n      messagesModule   the catalogs a caller loaded
com.strange.koin.redis     redisModule      one Redis connection
com.strange.koin.mongo     mongoModule      one client, and the database over it
com.strange.koin.amqp      amqpModule       one AMQP connection
com.strange.koin.kafka     kafkaModule      the cluster configuration
com.strange.koin.storage   storageModule    one object-storage client
com.strange.koin.jpa       jpaModule        one Hibernate Reactive session factory (jpaScanModule maps a package)
```

```kotlin
startKoin {
    modules(
        redisModule(RedisConfig(uri = System.getenv("REDIS_URI"), namespace = "orders")),
        mongoModule(System.getenv("MONGO_URI"), database = "orders"),
        appModule,
    )
}

class CartStore(private val redis: Redis, private val orders: MongoDatabase)
```

## Why this is not a package in stx-ktor

**These have nothing to do with Ktor.** A worker, a CLI or a consumer on Koin wants
`redisModule(config)` without a web framework on its classpath — the same argument that put the
plugins outside the libraries they wrap. The library knows the backend, `stx-ktor` knows the
framework, this knows the container, and none of them has to know two.

## Here the container creates it

That is the difference from the Ktor side, and it follows from who is in charge. A Ktor application
names its connection in an `install` block, so `provideRedis()` there registers *what the plugin
installed*; an application on Koin has no such block, so the module is where the connection is named
— and `onClose` is then how it is closed, since whoever created it should.

**In an application that has both**, let one of them own it and let the other adopt it:

```kotlin
install(RedisConnection) { instance = get() }
```

Opening one in the module and another in the plugin would be two connections where the application
meant one, and nothing would fail to say so.

## What has no `onClose`

`kafkaModule` and `messagesModule`. `Kafka` is a description of a cluster and opens nothing — what
holds a connection is a publisher or a subscriber built from it, so that is what a module should
register, one `onClose` each, since a publisher owns a producer. `Messages` is catalogs in memory,
loaded by the caller because a catalog comes from a resource, a database or a bundle and
`Messages.load` is where that choice already lives.

## The blocking calls

`amqpModule` and `jpaModule` use `runBlocking`, because both connects suspend — a socket, a
handshake and an authentication round trip for one; reading the annotations off every entity and
building a metadata model for the other — and Koin's `single { }` has no suspending form. It is the
same trade the Ktor plugins make at install, on the thread that is starting the application either
way. It is also a reason to resolve both eagerly at startup rather than on whichever request happens
to be the first to ask.

## One module without a fat dependency list

Every backend is declared `compile-only`, the way `stx-ktor` declares its own, and for the same
reason: an application that calls `redisModule` already depends on `stx-redis` — `RedisConfig` is
the only way to configure it — and one that calls only `kafkaModule` never loads a class from any of
the others. `koin-core` is the exception and is exported, since `Module` is this library's entire
public API.
