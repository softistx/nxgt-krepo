# stx-workflow-db

Where [`stx-workflow`](../stx-workflow/README.md) instances live. One package per backing store,
one module for all of them.

```kotlin
val engine = WorkflowEngine(RedisWorkflowStore(redis)) { register(checkout) }
```

**Every store here takes a connection it did not open and does not close.** Whoever created the
`Redis`, the `Jpa` or the `MongoDatabase` closes it, which is what lets one connection serve a
cache, a lock and this at the same time — and is why none of these classes is `AutoCloseable`.

## One module, and not three

The same bargain `stx-ktor` makes for its seven integrations: **every backend is `compile-only`**,
including the ones this module's API returns. `RedisWorkflowStore(redis)` names a `Redis` in its
signature and Lettuce still stays off a consumer's runtime classpath.

That works here for a reason worth stating rather than assuming. A store cannot be *constructed*
without the library that provides its connection — there is no `uri` parameter anywhere in this
module — so an application that uses one already depends on it, and an application that uses only
Redis never loads a class from Hibernate or the Mongo driver. Nothing is missing at runtime and
nobody carries three drivers to use one.

What one module buys, beyond the arithmetic: the parts that are the *same* across stores are written
once, and checked once. `Lease` — in `stx-common`, since `stx-migrations-db` runs the same policy —
is `guarded` for the two stores with no lock to borrow. `StoreContract`
is the eleven scenarios all three answer — and it caught a real one on its first run, where
`RedisWorkflowStore.create` put a parked instance in the due-time index that `save` kept out of it.
Three sibling modules would have had three copies of each and would have drifted the first time one
of them was fixed.

## Which one

| Store | Wants | Notes |
| --- | --- | --- |
| `RedisWorkflowStore` | a `Redis` from `stx-redis` | The fastest, and the only one with a real TTL — a finished instance expires on its own |
| `JpaWorkflowStore` | a `Jpa` from `stx-jpa` | Postgres, DB2, MySQL — whatever the URI's scheme picks. Instances land in the same transaction log as the application's own rows |
| `MongoWorkflowStore` | a `MongoDatabase` from `stx-mongo` | One document per instance, and a TTL index the server enforces |

Verified against Redis, Postgres and MongoDB. DB2 and MySQL come out of the relational store by
construction — it writes no SQL — and are unrun here.

## Redis

### Keys

Everything goes through `redis.key(…)`, so a caller's `RedisConfig.namespace` prefixes all of it.

```
<ns>:wf:instance:<id>   HASH   record = the instance as JSON, version = the number the write checks
<ns>:wf:runnable        ZSET   id -> the epoch-millis it is next due
<ns>:lock:wf:<id>       the RedisLock guarding one instance
```

The instance key carries no workflow name. `load` is handed an id and nothing else, so a key it
cannot build from an id alone is a key it cannot read.

**The version is a field of the hash, not part of the document.** The conditional write compares it
in Lua, and parsing the whole record to read one number would be both slower and a second copy of a
value that has to stay true.

### A sorted set, not a stream

An instance waiting to be advanced is not an event. It is a **state**, and it stays true until
something changes it — which is exactly what a sorted set holds and what a stream does not.

- `ZRANGEBYSCORE <ns>:wf:runnable 0 <now>` answers the worker's whole question in one round trip.
- Re-scoring an id updates it **in place**. A stream would have grown one entry per checkpoint, and
  left a pending list to reconcile against the record that already knows the answer.
- A `sleep`'s wake-up and an `await`'s deadline are both "due at T", which is a score — the same
  set answers all three questions with no second index.

**An instance waiting on a signal with no deadline is not in the set at all.** It is alive and
unfinished, and still nothing polls it: no amount of time will move it, so offering it to a worker
would be offering work that cannot be done — a loop whose cost grows with how patient the business
process is. `WorkflowRecord.isParked` is that rule, and the `SAVE` script takes such a record out of
the index the same way it takes a finished one out, minus the retention TTL.

**The score doubles as a visibility window.** A running instance is scored one lease into the
future, so a worker leaves alone one somebody is plainly working on; when that somebody dies, the
score passes and it is offered again. That is the whole recovery mechanism — there is no separate
claim, no heartbeat, and nothing to reconcile.

### The write is one Lua script

A `HSET` that landed without its `ZADD` would leave an instance nothing polls for — invisible until
somebody resumed it by hand — and a `ZADD` without its `HSET` would send a worker after a record that
had not changed. `MULTI` would also make the pair atomic, but only on a connection of its own,
because it blocks the multiplexed one every other command shares; a script does it on the shared
connection in one round trip.

The same script does the version check, so "write if nobody else has" is one operation and not a
read followed by a hopeful write.

### The lock, and why it is held across the work

`guarded` is `RedisLock(redis, "wf:$id", lease).withLockOrNull { … }`, with `wait = ZERO` and the
watchdog on.

- **`wait = ZERO`**: a caller that cannot get in should go and do something else. Whoever holds it is
  already advancing this instance, and queueing behind them ends with every worker in a fleet parked
  on the same slow step.
- **Held across suspending work, on purpose.** AGENTS.md's rule against that is about an in-process
  `Mutex`, whose cost is queueing unrelated callers behind one. This is a lease on a resource, and
  holding it for the duration is the point — releasing it mid-step would let a second worker start
  the same step. The engine holds no in-process lock at all.
- **`renew = true`** — the default — is what keeps a step slower than the lease from silently losing
  the lock underneath itself. The lease is therefore not a deadline on user code; `timeout` is.

`lease` defaults to 30 seconds: long enough that a live runner is not undercut, short enough that
recovery is not a coffee break.

**It is a lock on one Redis, not Redlock.** A failover to a replica that had not seen it can produce
two runners of one step. That is why every write is also conditional on the record's version, and
why the core README's rule stands: step bodies must be idempotent.

### The status index

One sorted set per status, `wf:status:<STATUS>`, scored by `updatedAt`. One set per status rather
than one for everything, because the query it exists for is *"show me the ones that need a person"*
and a single index would have buried `Failed` under every instance that ever completed.

The Lua builds these keys itself, from a prefix, because a write has to remove the id from its **old**
status's set and the old status is only known once the hash is read. That is a key the script does
not declare, so this store assumes a single logical Redis rather than a cluster — the same assumption
`scanKeys` in `stx-redis` makes.

`find` prunes as it reads: a terminal instance's hash expires under the retention TTL and nothing
expires a sorted-set entry, so an id pointing at a hash that is gone is removed on sight. `Failed` is
exempt from the TTL, so the one index an operator actually reads never rots to begin with.

### Retention

A finished instance leaves the due-time index and takes a TTL — `retention`, seven days by default, `null` to
keep it forever. A completed run is the audit trail somebody will want afterwards, and an engine
whose finished instances vanish is one that cannot be debugged; but Redis holds it in memory, so the
default is a bound rather than nothing.

`Failed` is exempt. It is waiting for a person, and expiring it would delete the only description of
what has to be fixed.

## Relational

`JpaWorkflowStore(jpa)` needs one thing from the application: `WorkflowInstanceRow` among the
entities it connects with.

```kotlin
val jpa = Jpa.connect(config, Order::class, WorkflowInstanceRow::class)
val engine = WorkflowEngine(JpaWorkflowStore(jpa)) { register(checkout) }
```

**Postgres, DB2 and MySQL come out of the same code.** Nothing in the store is SQL — every statement
is HQL, and Hibernate picks its dialect from the URI, exactly as it does for the application's own
entities. Only Postgres is *verified* here; the other two are supported by construction and unrun.

### One row, and the columns a query needs

`record` is the encoded `WorkflowRecord` and nothing reads inside it. `due_at`, the lease pair,
`finished_at`, `status` and `updated_at` exist because a query needs them — a `where` cannot look
inside a string. `status` and `updated_at` are the only two facts duplicated out of the document, and
they cost nothing worse than a row that should not have been in a page: `find` returns the *decoded*
record, so the columns filter and order and are never read as truth.

`record` is `Length.LONG32`, not `@Lob`. That is `text` on Postgres and `clob` on DB2 — a column
psql shows you. A `@Lob String` on Postgres has historically been a large-object `oid`, which
`select` shows as a number, and a journal an operator cannot read is not there when it is needed.

### The conditional write is one statement, not `@Version`

Hibernate's optimistic locking signals a lost race by *throwing* at flush. Honouring
`save(record, expectedVersion): Boolean` with it would mean a select, a mutation, a rolled-back
transaction and an exception caught across it — to answer a boolean. `where version = :expected` is
the same guarantee in one `update`, with the row count as the answer.

### A lease column, not `select … for update skip locked`

A row lock lives inside a transaction, so holding one across a step means holding a database
connection for as long as the step runs — and `skip locked` is spelled differently on every dialect.
Two columns are a lease that outlives the process that took it, needs no open transaction, and reads
the same everywhere. It renews while the work runs, for the reason `RedisLock` does: it says *this
instance is being advanced right now*, not *this code is short*.

### Retention is a job, not a TTL

A table has no TTL, so `purge(before)` deletes finished instances and answers how many. Calling it is
the application's business — a library that quietly deleted rows out of somebody's own schema on a
timer would be a surprise nobody signed up for. `Failed` is exempt here too: it never gets a
`finished_at`, so nothing takes it.

## MongoDB

```kotlin
val store = MongoWorkflowStore(database)
val engine = WorkflowEngine(store) { register(checkout) }
```

**A suspending function, not a constructor.** It creates the three indexes the store needs before it
hands one back: `dueAt`, which is what makes `runnable` a ranged read instead of a collection scan,
a TTL on `expiresAt`, and `(status, updatedAt)` for `find`. Creating them lazily would leave the first `runnable` of a fresh deployment
scanning; leaving them to the caller would give a store that works and quietly degrades.

`record` is the encoded `WorkflowRecord` as a **string**, not a nested document. The context inside
it is already a `JsonElement` and no BSON codec maps one, so the choice was between a string the
store never looks inside and a mapping that would have to. `status` and `updatedAt` sit beside it as
their own fields, for the same reason the relational store has those two columns: `find` filters and
orders on them, and a query cannot look inside a string.

### Retention is the server's job here

`expireAfterSeconds: 0` on `expiresAt` means *delete when this date passes*. A finished instance gets
one, `retention` from now — seven days by default, `null` to keep it forever. A document with no
`expiresAt` is never taken, which is how everything still running stays put, and how `Failed` stays
put: it is waiting for a person, and expiring it would delete the only description of what has to be
fixed.

This is the same rule all three stores follow, spelled three ways — a TTL on the key in Redis, a TTL
index here, and `purge(before)` on a schedule where SQL has no TTL to lean on.

### A lease pair, because there is no lock to borrow

Two fields an `updateOne` sets and another clears, and the same `Lease` policy the relational store
uses. A lock collection of its own would be these two fields in a second document, plus the problem
of keeping the two documents in step.

## The worker is not here

`WorkflowWorker` lives in the core module. It asks the engine what is due and resumes it, which is
two methods on `WorkflowStore` and nothing about any particular backend — so putting it beside the
one store that existed at the time would have meant moving it the day the second one arrived. It
did, and it was already gone.

What *is* here is the half that makes it work, once per store: an index that answers "what is due"
in one round trip, and a lease that lets several workers pull the same id without a claim protocol
between them.
