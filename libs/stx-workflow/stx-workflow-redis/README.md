# stx-workflow-redis

The `WorkflowStore` of [`stx-workflow`](../stx-workflow/README.md) on Redis, and the worker that
picks up instances nobody is advancing.

```kotlin
val redis = Redis.connect(RedisConfig(uri, namespace = "billing"))
val engine = WorkflowEngine(RedisWorkflowStore(redis)) { register(checkout) }

// optional, and nothing starts it for you
WorkflowWorker(engine).use { it.start(applicationScope) }
```

It takes a connection it did not open and does not close — whoever created the `Redis` closes it,
which is what lets one connection serve a cache, a lock and this at the same time.

## Keys

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

## A sorted set, not a stream

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

## The write is one Lua script

A `HSET` that landed without its `ZADD` would leave an instance nothing polls for — invisible until
somebody resumed it by hand — and a `ZADD` without its `HSET` would send a worker after a record that
had not changed. `MULTI` would also make the pair atomic, but only on a connection of its own,
because it blocks the multiplexed one every other command shares; a script does it on the shared
connection in one round trip.

The same script does the version check, so "write if nobody else has" is one operation and not a
read followed by a hopeful write.

## The lock, and why it is held across the work

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

## Retention

A finished instance leaves the index and takes a TTL — `retention`, seven days by default, `null` to
keep it forever. A completed run is the audit trail somebody will want afterwards, and an engine
whose finished instances vanish is one that cannot be debugged; but Redis holds it in memory, so the
default is a bound rather than nothing.

`Failed` is exempt. It is waiting for a person, and expiring it would delete the only description of
what has to be fixed.

## The worker is not here

`WorkflowWorker` lives in the core module. It asks the engine what is due and resumes it, which is
two methods on `WorkflowStore` and no Redis at all — so putting it beside the one store that exists
today would have meant moving it the day a second one arrived.

What *is* here is the half that makes it work: a sorted set that answers "what is due" in one round
trip, and a lock that lets several workers pull the same id without a claim protocol between them.
