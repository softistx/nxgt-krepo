# stx-migrations

Schema migrations written in Kotlin. A migration is a class with a version on it; a **ledger** records
what has run; a **lock** keeps two processes from running the same one twice; and the whole thing is a
**gate** at startup — nothing serves until the migrations are done, and a failure is an application
that does not start.

**`io.github.softistx:stx-migrations`** — [how to depend on it](../../../../docs/consuming.md).

This module holds none of that ledger. `MigrationLedger` is an interface here and nothing in this
module knows what a database is; the implementations live in `stx-migrations-db`, one package per
store, and `stx-migrations-ktor` / `stx-migrations-spring` are the two framework integrations.

```kotlin
class V1Orders : SqlMigration {
    override val version = 1L
    override val description = "the orders table"

    override suspend fun migrate(context: SqlMigrationSession) {
        context.execute(
            """
            create table if not exists orders (
                id bigint primary key,
                total numeric(12, 2) not null
            )
            """,
        )
    }
}

val ledger = SqlMigrations(jpa, listOf(V1Orders(), V2OrderIndex())).run()
```

## Why this exists at all

The repo had a MongoDB migration runner buried inside `stx-spring-boot`, bound to `ReactiveMongoTemplate`
and to a Spring `ApplicationContext`. A Ktor application on `stx-mongo` had no migrations; a SQL
application had none either, and `stx-jpa`'s `SchemaMode` says why that matters — *"a schema is
migrated by something that keeps a history, not by an ORM inferring one from the classes it happens to
have been given."*

Neither Flyway nor Liquibase is underneath this, and both were tried before they were ruled out.
Liquibase Community is **FSL-1.1-ALv2** since 5.0 — source-available, with a *Competing Use* clause
that names a product offering substantially similar functionality, which a published migration library
is. Flyway Community is Apache 2.0 and was the plan of record until a spike measured its MongoDB
support: about nine coordinates that do not arrive transitively, a relational row pasted into a
document with `installed_on` as a **string**, null schema versions on a successful run, and no lock on
the Native Connectors path. What Flyway genuinely earns — dialect knowledge, checksums, contexts,
`undo` — is unused or unwanted here, and it would have forced JDBC into a repo that has none.

## The version is declared, not parsed

```kotlin
override val version = 1L
```

The runner this replaces read the number out of `^V(\d+)([A-Za-z_][A-Za-z0-9_]*)$` against the bean's
class name. That bought a naming convention and cost a configurable prefix, an ambiguity about whether
`V102` is version 102 or version 10, a CGLIB `$$` proxy name to strip before matching, an
`AnnotationUtils.findAnnotation` dance for a description, and — the one that matters — a class the
regex rejected, which ran nothing and said nothing about it.

`stx-workflow`'s `Nodes.order` had already settled this the other way, and this is the same answer: the
number is an abstract property, so the compiler is what checks it is there. `@MigrationUnit` is gone
with the regex; `description` is a property with a default now, so the annotation had nothing left to
contribute.

A `Long` rather than an `Int`, because the other convention for this number is a timestamp —
`20260901120000` — and that does not fit in an `Int`.

## Three statuses, and the one that is new

| | |
| --- | --- |
| `RUNNING` | claimed and being applied — or by a process that died while applying it |
| `APPLIED` | done; skipped forever after |
| `FAILED` | it threw; nothing after it ran, and nothing will until a person has looked |

There is no `PENDING`, because there is nothing for it to mean: a migration the ledger has never heard
of *is* pending, and writing a row to say so would be a write before the lock is held.

**`RUNNING` is the whole point.** The prior art had `PENDING`/`APPLIED`/`FAILED` and no lock, which
meant a process killed halfway through a migration left a ledger indistinguishable from one where the
migration had never been attempted — and the next startup ran it again without a word. With a lock and
a `RUNNING` sentinel that failure is fail-closed: the next run finds a `RUNNING` record older than
`staleAfter`, refuses, and names the version and the host that claimed it.

`staleAfter` is only ever consulted **while this run holds the lock**, which is what makes "stale"
unambiguous. A process that were still alive would still be holding the lock, and this call would not
be happening.

## A gate throws where the prior art logged

`MigrationRunner.run()` throws four things, and every one of them leaves the call:

| | |
| --- | --- |
| `DuplicateMigrationVersionException` | two migrations claim one version — thrown when the runner is **built** |
| `MigrationHaltedException` | the ledger holds a `FAILED` record, or a stale `RUNNING` one |
| `MigrationFailedException` | `migrate` threw; the record says `FAILED` and nothing after it ran |
| `MigrationLockTimeoutException` | somebody else held the lock for longer than this run would wait |

The runner this replaces logged each of these and carried on, which is how a deployment ends up
answering requests against a schema that was never migrated. It was also a suspending
`@EventListener(ApplicationReadyEvent)`, and `SuspendingListenerTest` in `stx-spring-boot` pins that
**Spring does not wait for a suspending listener** — the port opened while migrations were still
running, and the example application worked around it by polling the ledger from its own specs.

Stopping at the *first* failure rather than trying the rest is the same rule, one level down:
migrations are written against the state the previous one left.

## The lock waits; `WorkflowStore.guarded` declines

`MigrationLedger.guarded` declines — that is the contract, and it is the same shape as
`WorkflowStore.guarded`. The difference is what the **runner** does with the refusal: it waits, up to
`lockTimeout`, polling every `lockPoll`.

That is deliberate and it is the opposite of the workflow decision. A fleet of workers declining a busy
instance goes off and does other work. A fleet of application instances declining the migrations would
go off and *start serving*, against a schema that does not exist yet.

The lock itself is `Lease` from `stx-common`: it declines rather than queues, renews at a third of its
duration while the work runs, and releases under `NonCancellable`. The prior art argued against a lock
— *"a lease that expires while a long migration is still running is a worse failure than the one it
prevents"* — and that is an argument against a **fixed** lease. A renewing one expires only when the
process holding it is gone, which is exactly when you want it to.

## No rollback, no checksum

**No `rollback`**, carried from the prior art's own KDoc: a declared-but-uncalled one *"reads as a
promise that a failed migration is undone, and no code anywhere kept that promise."* A change that has
to be undone is undone by the next migration — a thing the ledger records and a person reviewed.

**No checksum.** Flyway hashes a file because a file can be edited in place after it ran. A migration
here is a class in the repository: the diff is the review and `git log` is the history, and a hash
could only tell you later, and from a database, what a `git blame` already tells you.

**A migration must be safe to attempt twice** — not because the runner will, but because a process
killed between the change and the ledger write leaves the change made and the record saying `RUNNING`,
and the operator clearing that is choosing between running it again and editing the ledger by hand.
`create table if not exists`, an `updateMany` filtered on the documents that still need it, `add column
if not exists`: each of those makes the choice easy.

## The ledger is seven methods

`prepare`, `find`, `claim`, `update`, `blocking`, `all`, `guarded` — and every one of them is something
a store does differently. Everything else is the runner, written once: the ordering, the duplicate
check, the halt rule, the waiting, the timing, the `appliedBy`.

Nothing is `AutoCloseable`. A ledger takes a connection it did not open, which is `stx-workflow-db`'s
rule and holds for the same reason: the application that built the `MongoDatabase` or the `Jpa` is the
one that closes it.

`prepare()` is called by the **runner**, not by whoever built the ledger, so nobody can forget it.
That is why the implementations are ordinary constructors rather than the suspending factory functions
`MongoWorkflowStore` uses — there is no index to create before the object is usable, because creating
it is the first thing `run()` does.

## Why this module has no store in it

`InMemoryLedger` is the reference implementation, and it is not a stub: the claim really is conditional
and the lock really declines. That is what lets `MigrationRunnerTest` — the order, the duplicate check,
the halt rule, the timeout, the release-on-failure — run in **640 ms with no container**. Merged into
`stx-migrations-db`, every one of those would be red on a machine without Docker.

It is also the artifact an application implementing a ledger of its own depends on, without pulling in
a Mongo driver and a Hibernate it has no use for.

## Vocabulary — see `docs/migrations.md`

Writing a migration, the statuses and their transitions, the ledger contract in detail, what a killed
process leaves behind per store, and what a migration is required to be. That file is the half that
grows; this one stays roughly the size it is.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
