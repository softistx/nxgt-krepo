# Shared code

`libs/core/stx-common` holds what **more than one module** needs, expressed without knowing anything
about any of them. It depends on kotlinx-coroutines and kotlinx-serialization and on nothing else,
ever: the moment something in there knows what a topic or a collection is, every library depending
on it inherits that, and a shared module that depends on everything is a cycle waiting for its
second commit.

Look there before writing a helper, and move one there when a *second* caller appears — not in
anticipation of one. A helper with a single caller belongs next to it, where it can be read
alongside the code that explains why it exists.

The concurrency types are not interchangeable, and the **first** question is which package: can the
caller suspend? `coroutines/` is for the ones that can — `CoroutineSafeMap` when each operation
stands alone, `KeyedMutex` when the work behind a key suspends and only callers wanting the *same*
key should wait, `Mailbox` when a producer is not a coroutine at all and must not be made to block.
`concurrent/` is for the callers that genuinely cannot suspend — a Hibernate binder, an SLF4J static
initialiser, a shutdown hook — and holds `Memo` for a value computed once per key, `Guarded` for an
object that is not thread-safe, and the `ConcurrentMap` and `BlockingQueue` extensions.
`libs/core/stx-common/README.md` has the reasoning; the short version is that reaching for `runBlocking`
to move from the second package to the first is how a client deadlocks against its own I/O thread.

**`getOrPut` on a `ConcurrentHashMap` is not atomic**, and this repository shipped that mistake in
`stx-jpa`'s serializer cache before it was written down. `kotlin.collections.getOrPut` compiles
against a concurrent map and is a get, a compute and a put with nothing holding them together, so two
threads at a cold key both run the loader — a wasted computation when the value is a plain result,
and a bug the moment it has identity. Reach for `Memo`, or `getOrCompute` when the loader differs per
call site. `MemoTest` pins the difference rather than asserting it.

**A data-access library offers extensions, not a base class to inherit from.** Neither
`stx-mongo` nor `stx-jpa` has a repository or a CRUD service class; the create/read/update/
delete vocabulary is extensions on `MongoCollection<T>` and on the JPA session. Two reasons, and the
first is the one that decides it: an extension takes `T` from its receiver or reifies it at the call
site, while a class cannot have a `reified` type parameter and so has to be handed a `KClass` or a
property reference to read an owner off — `session.findAll<Purchase>()` against
`JpaRepository(Purchase::id).findAll(session)`. And a repository over a collection turned out to be
one-line delegation twenty times over, with the overridable hooks its service used being the least
reusable part of either module. What a base class earned and an extension still has to provide is
kept explicitly: `insertAndRead`, the transaction guard on every JPA write verb, and the audit
stamps. When a new store is added, follow the same shape.

**Shared does not mean everything shared goes there.** `libs/api/stx-i18n` is used by more than
one module and is still its own library, because ICU4J is a 15 MB jar and `stx-common`'s rule
is kotlinx-and-nothing-else — putting message formatting in it would make `stx-kafka` carry a
formatting library it will never call. The same test applies to the next candidate: if it brings
a dependency, it brings that dependency to everything.

**A framework integration is a sibling module of the library it integrates**, in that library's own
family directory: `stx-jpa-ktor` beside `stx-jpa`, `stx-workflow-ktor` and `stx-workflow-spring`
beside `stx-workflow`. Every one of them, with no exception for the small ones — see *The exception
this rule used to have* below, which records why there was one and why it is gone.

Not in the framework module, because that makes it know every library in the repository: `stx-ktor`
held one package per integration, so **adding a library modified it**, and an application installing
one plugin read seven others' dependencies to get it.

And not *inside* the library — but not for the reason it first looks like. `compile-only` would keep
Ktor and Spring off a consumer's runtime classpath perfectly well, so a CLI taking `stx-jpa` would
pay nothing at runtime for an `integration/ktor` package sitting unused in the jar. What it would pay
is elsewhere, and the coupling does not disappear so much as **reverse direction**: instead of three
seams knowing seven libraries, seven libraries would each know three frameworks. The same edges,
now pointing from the things that must stay light to the heavy ones.

- `stx-jpa` would compile against both Spring Boot and Ktor, so a Ktor version bump recompiles all
  seven libraries.
- Its test module would carry a Ktor test host and a Spring context *beside* its Postgres specs. Today those three fail independently; merged, a broken Spring wiring spec fails the JPA
  library's build.
- Its manifest would stop describing what it is.

A sibling module has neither problem: `stx-jpa-ktor` compiles against Ktor and `stx-jpa` does not,
and the two test suites still fail independently.

`libs/core/stx-ktor` keeps what belongs to no library and is the same for all of them: the
resource-lifecycle idiom (`own`, `publish`, `resource`, `required`) and the one CORS policy shared
with Spring. It is the **seam**, not the switchboard, and its own specs are the tell — there is no
backend in any of them. Those four verbs are public rather than internal for exactly this reason:
they are the contract between the seam and every integration built on it, and a contract cannot be
internal. `libs/core/stx-spring-boot` is the same seam for Spring, and it has no `src/integration/`
either: what is left there is error handling, security, CORS, the JSON and web conventions, and the
Spring Data Mongo layer — none of which belongs to a `stx-*` library.

The framework modules know the framework, the libraries know the backends, and neither knows two.

There is no Koin seam. `libs/stx-koin` existed and was removed: these libraries are server-side, so
Ktor's own DI covers the callers that have a framework, and Koin interoperates with it for the ones
that prefer it. What the module actually held was one line per backend —
`single { Redis.connect(config) } onClose { it?.close() }` — which an application writes itself in
less time than it takes to find the dependency.

**A library edge in an integration module is `exported`, not `compile-only`.** This is the rule that
changed when the hub was emptied, and it is the reason the move was worth making rather than a
tidy-up. In `stx-ktor` every backend had to be `compile-only`: `call.redis` returns a `Redis` and
Lettuce still had to stay off the classpath of an application that only installed `I18n`. In
`stx-redis-ktor` there is no such application — `RedisConfig` is the only way to configure the
plugin at all — so the edge is `exported` and a consumer inherits it. The dependency stops being
optional because it stopped being one.

`compile-only` keeps its place for what an integration genuinely may not use: the store libraries in
`stx-migrations-ktor`, which offers two ledgers and expects a caller to want one, and `ktor-server-cors`
in `stx-ktor`, for the one function that calls it. `ktor-server-di` used to be on that list and is
not any more — a plugin registers what it installed with the container unconditionally, so every
application that installs one carries it. Verified the same way as before —
`./kotlin show dependencies -m <module>`: a compile-only entry sits in COMPILE and is absent from
RUNTIME.

**The exception this rule used to have.** It said the line was not an integration's size but whether
it had *a design of its own*: a module when it brought its own lifecycle and configuration surface —
`stx-workflow-spring` choosing between three stores, `stx-graphix-ktor` owning routes and a
websocket protocol — and a package in the seam when it was **the seam's idiom applied to one more
type**, since a published artifact each was said to cost more than it removed.

That was wrong twice over. It made the repository state one relationship in two shapes, so a reader
had to know which list an integration was on before knowing where to look for it. And the cost it
weighed was the wrong one: what an integration in the hub actually costs is that its library edge
can never be honest — six optional siblings force the seventh to be optional too.

The size argument was also weaker than it read. Each of the seven was 64–180 lines, and each still
had enough of its own to say to fill a README: why `KafkaCluster` owns nothing, why `Storage` is the
one whose `config` is required, why `MongoDB` builds its client through the library rather than
itself. An integration with nothing to explain would be an argument for deleting it, not for hiding
it in a hub.

So: every integration is a module beside its library, and a *new* one is written that way rather
than added to a seam.

The library knows the backend, the integration module knows the framework, and neither has to know
both. That is the whole of the rule, and it is why an integration is a module beside its library
rather than a package inside it.

**A framework integration must assume the resource is not its own, and must not be the only way to
reach it.** Three rules, and the next integration is built to them rather than retrofitted:

- **Take an instance as well as a config.** Every plugin's configuration has an `instance`; set it
  and the plugin adopts what an application or a container already built, instead of opening a
  second one.
- **Whoever created it closes it.** `Resources.kt` says this once, as `own` (we opened it, we close
  it on `ApplicationStopped`) and `publish` (someone else's, the *plugin* leaves it alone). A plugin
  that adopts a connection and also closes it is the second close. This is about the plugin, not
  the container: the plugin registers an adopted `instance` with Ktor's DI as well, and the
  container closes every `AutoCloseable` it has resolved at application stop, adopted ones
  included. An `instance` shared between two servers, or kept across a test bench's restarts, is
  closed under its owner; the way out is the application replacing `onShutdown` in `install(DI)`,
  shown in the `stx-ktor` README.
- **Reaching a resource only through `call.x` is a service locator.** A class a container builds
  has no `ApplicationCall`, so each plugin registers what it installed with Ktor's DI —
  `install(RedisConnection) { config = … }` and the same connection is then both `call.redis` and a
  constructor parameter. That is not a flag: injection is what installing the plugin does, `provideX`
  is `internal`, and `ktor-server-di` is an `exported` dependency of every integration module
  because of it.

**And close idempotently, through `CloseGuard`.** A resource that is handed around is closed more
than once, and the rule above says who *should* close it, not what happens when two of them do.
Ktor's DI closes every `AutoCloseable` it hands out at application stop — one a provider merely
passed through included, and a per-key `cleanup` runs beside that hook rather than instead of it, so
a library cannot opt out. The drivers do not agree here either: Lettuce and the MinIO client tolerate
a second close, the RabbitMQ client throws. Any new `AutoCloseable` in these libraries closes through
the guard, so that all of it stays a question of tidiness rather than of correctness.

`libs/core/stx-spring-boot` is the same seam for Spring that `stx-ktor` is for Ktor, and it follows the
same `compile-only` rule for the same reason. Two things about it are specific to this toolchain and
neither is guessable:

- **Nothing it registers is on until a property asks for it.** Every bean is
  `@ConditionalOnProperty(prefix = "stx.<name>", name = ["enabled"], havingValue = "true")` with **no
  `matchIfMissing`**, and every one is `@ConditionalOnMissingBean` so an application's own bean wins.
  Putting the module on a classpath starts nothing — which is what makes it safe for an application
  that already has an exception handler of its own.
- **The IDE metadata is written by hand, and a spec keeps it honest.**
  `spring-boot-configuration-processor` is a *Java* annotation processor; the toolchain has no kapt,
  and `settings.java.annotationProcessing` runs javac over Java sources only — so the processor never
  sees a Kotlin `@ConfigurationProperties` class and generates nothing, silently. The keys therefore
  live in `resources/META-INF/additional-spring-configuration-metadata.json`, Spring Boot's own
  supported manual file, and `ConfigurationMetadataTest` scans the module for
  `@ConfigurationProperties` classes and fails when a property has no entry or an entry has no
  property. **A new `stx.*` key is added to that file in the change that reads it** — and to
  `docs/spring-configuration.md`, which is where a reader looks. Do not reach for the processor; it
  will appear to be configured and produce nothing.

One more thing that only a test says out loud: `compile-only` keeps a dependency off the *test*
runtime too, so a `@ConditionalOnClass` guarding it correctly declines to match in a spec. Add the
dependency to `test-dependencies` at normal scope — the same shape every `*-ktor` module uses for
`ktor-server-di` — rather than
weakening the condition.
