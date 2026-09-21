# artifacts-ktor

Not a demonstration. This module and [`artifacts-spring`](../artifacts-spring/README.md) exist to
resolve nine published coordinates that nothing else in this repository resolved.

## The gap they close

Every other module under `examples/` earns its place twice. It shows a library being used, and —
because it names `io.github.softistx:stx-*` rather than `//libs/...` — it proves the published POM
carries what a consumer needs, at the scope a consumer needs it. Both times an artifact of this
repository was wrong, an example is what found it.

`stx-amqp`, `stx-kafka` and `stx-storage` had neither. Their nine artifacts were published on every
release and consumed by nothing, so a POM of theirs could lose a dependency, or move one to
`provided`, and every check here would stay green.

## What each half proves

| | |
| --- | --- |
| **the compile** | A call site only resolves if the POM carries what the signatures reach for. `AmqpConfig`'s `configure` parameter is typed `(ConnectionFactory) -> Unit`, so constructing one at all requires `stx-amqp` to have exported the RabbitMQ client rather than kept it to itself. |
| **the spec** | Loading the classes proves the jars resolve and that nothing they touch on the way in is missing. |
| **[`artifacts-spring`](../artifacts-spring/README.md)** | Boots a Spring context, which is the only way to find out that each `-spring` artifact's `AutoConfiguration.imports` survived publication and that the class it names loads. |

None of it needs a broker, a cluster or an object store. The libraries' own specs under
`libs/*/stx-*/test/` drive them against real services; this one only ever asks whether a consumer
could obtain them.

```bash
./kotlin publish mavenLocal $(bun scripts/modules.ts --publish-args)
./kotlin test -m artifacts-ktor -m artifacts-spring
```

The publish comes first, always. These modules read `~/.m2`, so a stale artifact there is what they
would be testing otherwise — which is the failure they exist to expose.

## What they are not

A real example for `stx-amqp`, `stx-kafka` and `stx-storage` — a service that publishes and
consumes, that stores and retrieves — is still worth writing, and would replace these. Until then
the coordinates are at least checked, and this file says plainly which of the two things you are
looking at.
