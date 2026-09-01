# workflow-checkout

A checkout as a compensating workflow, showing [`stx-workflow`](../../libs/stx-workflow/stx-workflow/README.md):
a step with a compensation, a fan-out whose legs are undone independently, a fork that stays decided,
and a run that outlives the process that started it.

```bash
./kotlin run -m workflow-checkout
```

It needs a Redis on `localhost:6379` — the workspace's own will do. It writes to **database 15**
under its own namespace and deletes that namespace on the way out.

There is no HTTP in here. `stx-workflow` has no Ktor or Spring integration yet, so the honest shape
for a demo is a `main` that runs three checkouts and narrates them.

## The files

| | |
| --- | --- |
| `src/Order.kt` | The context — one `@Serializable` type threaded through every node, every field defaulted |
| `src/Services.kt` | The three services a checkout talks to, faked. `Payments` is the one to read |
| `src/CheckoutWorkflow.kt` | The declaration. It is the whole of what happens, and of what is undone |
| `src/Checkout.kt` | The three runs, and the journal printed after each |
| `test/CheckoutWorkflowTest.kt` | The same three runs asserted, over `InMemoryStore` — so this is verified on every build, not only when somebody has a Redis up |

## What each run shows

**1 — it works.** Six journal entries for four things you wrote, because a fan-out records each leg
and the fork records which arm it took:

```
Succeeded  reserve
Succeeded  provision/charge
Succeeded  provision/tracking
Succeeded  provision
Succeeded  notify
Succeeded  notify/express/notify-express
```

**2 — the courier has no van.** The fan-out is retried, and the retry re-runs **only** the leg that
had not succeeded — the card is not charged again on the way to failing. When the retries are gone,
the legs that did succeed are undone, then the steps before the fan-out, newest first:

```
Succeeded    reserve
Succeeded    provision/charge
Compensated  provision/charge
Failed       provision/tracking  (2 attempts)
Compensated  reserve
```

The instance ends `Compensated` — which is an ordinary outcome, not a fault. `Failed` is reserved
for a workflow whose *undo* failed, and that one needs a person.

**3 — the process dies mid-charge.** This is the one worth reading the code for. The fake gateway is
told to take the money and *then* hang, so the process is killed in the one window the whole design
turns on: **the effect has happened and the checkpoint has not.**

```
payments:  charged 4200 from 4242 -> chg-1   [key checkout:ord-3:provision/charge]

-- the process is gone --
status  Running
journal
  Succeeded  reserve
the charge is not in the journal, and the money has already left.

-- a new process picks it up --
payments:  'checkout:ord-3:provision/charge' was charged already -> chg-1; no money moved
```

A second engine — a restarted process, as far as Redis is concerned — runs the charge again, because
nothing recorded that it had run. The gateway recognises the key and moves no money.

**That is the contract, both halves of it.** Delivery is at-least-once because the checkpoint is
written after the effect; the alternative loses work. What makes that safe is `idempotencyKey` — the
same string on a retry, on a resume, and in another process — and `Payments.charge` here is what a
real gateway does with it.

The lock the dead process held is not released by a coroutine being cancelled. It is left to expire,
which is what `RedisWorkflowStore`'s lease is for, and why the third run waits before resuming.

## Two things worth copying

`redis.deleteKeys(redis.key("*"))`, not `deleteKeys("*")`. The pattern is passed to `SCAN` as given
and is **not** prefixed with the connection's namespace, so the bare one empties the whole database.

The workflow is a function of its collaborators — `checkoutWorkflow(warehouse, payments, …)` — so the
declaration is built once at startup and a step body closes over what it needs. Nothing in it belongs
to one run; that is what the context is for.
