# nxgt-krepo

A multi-module Kotlin repository built with the **JetBrains Kotlin Toolchain** (the `kotlin` CLI,
formerly Amper) — no Gradle, no Maven, no `gradlew`. A module is a directory with a `module.yaml`,
registered by path in `project.yaml`.

Its first subject is an **OpenAPI-to-Kotlin client generator**, built as four modules that form one
chain:

```
apps/demo-api/openapi.yaml     one document
        │
libs/openapi-generator         reads it, emits models and a typed client (KotlinPoet)
        │
plugins/openapi                wraps the generator as a toolchain build task
        │
apps/demo-client               a Ktorfit client, kotlinx.serialization  ─┐
apps/demo-spring-client        a Spring @HttpExchange client, Jackson 3 ─┴─ both call apps/demo-api
```

Two generated clients drive one hand-written server over real HTTP, so a disagreement between the
two serialization libraries about what the document means fails a test rather than shipping.

Alongside it are the shared service libraries, which have nothing to do with the generator:

| | |
| --- | --- |
| `libs/shared-mongo` | Session-aware collection extensions, keyset pagination, a CRUD repository and the write flow over it, and a coroutine GridFS bucket |
| `libs/shared-redis` | A namespaced connection over Lettuce, and the four things built on one: a typed cache, a lock, topics, and streams with consumer groups |

## Getting started

```bash
./kotlin build          # compile everything
./kotlin test           # run every module's tests
./kotlin show modules   # module names accepted by -m
```

Use `./kotlin`, not a bare `kotlin`: the wrapper pins the toolchain version.

## Where to read next

| | |
| --- | --- |
| [`docs/openapi-support.md`](docs/openapi-support.md) | What the generator understands: type mapping, composition, enums, vendor extensions, and what it does not handle |
| [`libs/openapi-generator/README.md`](libs/openapi-generator/README.md) | The generator itself — its shape, what each client emitter produces, how to add one |
| [`libs/shared-mongo/README.md`](libs/shared-mongo/README.md) | The MongoDB library — its packages, and the reasoning behind the parts that are not obvious |
| [`libs/shared-redis/README.md`](libs/shared-redis/README.md) | The Redis library — the cache, the lock, topics and streams, and what each one refuses to do |
| [`plugins/openapi/README.md`](plugins/openapi/README.md) | The build plugin: settings, and what each choice needs on the consuming module's classpath |
| [`AGENTS.md`](AGENTS.md) | Build commands, module layout, and the conventions this repo holds itself to |
