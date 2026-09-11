# stx-telemetry-mongo

Logs and traces into a MongoDB collection.

**`io.github.softistx:stx-telemetry-mongo`** — [how to depend on it](../../../docs/consuming.md).

```kotlin
Telemetry("checkout") {
    export(MongoExporter.connecting("mongodb://localhost:27017", retention = 30.days))
}
```

Or, in Spring Boot, `stx.telemetry.mongo.enabled=true` — see
[`docs/spring-configuration.md`](../../../docs/spring-configuration.md).

## Why the document looks like the file

It goes through `signalJson` first, the same configuration `JsonLinesExporter` and `FileExporter`
write. So a query against the collection reads like a `jq` filter against a file, and a team that
starts with a file and moves to a database keeps what it had written. A hand-rolled document shape
would have been one more thing to keep in step with the model, and it would have made the two
formats diverge on the first field somebody added.

## Except the instants, which are dates

JSON has no date, so `signalJson` renders an `Instant` as an ISO-8601 string — and a string is not
something Mongo will expire, compare or usefully index. `at`, and a span's `startedAt` and `endedAt`,
are written back from the typed signal after the conversion. **From the signal, not fished out of the
JSON by name**: the second would go quietly wrong the day a field is renamed, and quietly is the
failure mode this whole library exists to avoid.

## And the resource, which the line format does not carry

A file belongs to one service. A collection does not. `service`, and `version` and `environment` when
set, are on every document, because the alternative is a collection nobody can filter — and the
service name is the attribute every backend groups by.

## Retention is an index, not a job

`retention` is a TTL index on `at`. Mongo's own background task does the deleting, on the primary,
whether or not this process is up. That is the argument for a database exporter over a table in a
SQL schema, where the same thing is a nightly `DELETE` somebody has to own and monitor.

Two consequences worth knowing:

- **It is built on the first batch, not at construction.** Creating an index suspends, and nothing
  that builds an exporter does — a `Telemetry { }` block, a Spring `@Bean` method. Building it here
  also means a database that was down at startup is not a permanent failure: the flag is set only
  once it worked, so the next batch tries again.
- **A changed retention rebuilds it.** Mongo refuses a second index with the same key and different
  options, so an application going from thirty days to seven would otherwise keep the thirty for
  ever and never be told. The old index is dropped and the new one built, which on a large collection
  is minutes of background work — against a setting that silently does nothing.

## Whose client it is

`MongoExporter(database)` owns nothing; `close()` leaves the client alone, which is the rule every
integration in this repository follows. `MongoExporter.connecting(uri)` opened the client and closes
it.

The Spring auto-configuration uses `connecting`, and that is a decision rather than a convenience:
**telemetry on its own pool** means a burst of it cannot exhaust the one the business requests are
queueing for, and a collection that has become slow cannot become a slow checkout. It also means the
exporter works in an application with no Mongo at all, which is the usual case for a service that
only wants somewhere durable to put its logs.

## Not built on stx-mongo

`libs/stx-mongo` is sessions, cursor pagination, GridFS and auditing. An exporter needs a collection
and an insert. Depending on it would pull the Reactive Streams driver into every application that
wanted telemetry in a database, to reuse nothing.

## Specs

`DocumentsTest` is pure — the field names, the dates, and that a whole number survives the trip
through JSON as a `Long` rather than coming back as `200.0`. `MongoExporterTest` needs a server and is
gated on it: a machine with neither Docker nor `MONGO_TEST_URI` skips those rather than failing a
build over something that is not the code.

```bash
./kotlin test -m stx-telemetry-mongo                                  # container for the run
MONGO_TEST_URI=mongodb://localhost:27017 ./kotlin test -m stx-telemetry-mongo   # a server already up
```

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
