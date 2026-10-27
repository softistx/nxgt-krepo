# stx-workflow

## 0.4.1

### Patch Changes

- [#343](https://github.com/softistx/nxgt-krepo/pull/343) [`2ed7ccc`](https://github.com/softistx/nxgt-krepo/commit/2ed7ccc53870c19e6fc3f9dfcb13495ec42aac6f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `retryCompensation` now recovers an instance the engine set to `Failed` because its stored context no longer decoded. Once the context decodes again (the stored context repaired, or the definition fixed), it runs the instance's unwind; for a child, retry the child and then its parent. While the context still does not decode, the call keeps refusing with `WorkflowNotRetryableException`.

- [#335](https://github.com/softistx/nxgt-krepo/pull/335) [`22e5e6e`](https://github.com/softistx/nxgt-krepo/commit/22e5e6ecf8cea2046e25339d85db368f18c66f98) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Internal: `WorkflowEngine.kt` is split by concern. `WorkflowEngineBuilder` has its own file, and the advancing machinery (`advance`, `signal` delivery, `startChild`) and the operator verbs' bodies (`undo`, `undoChild`, `retryCompensation`) moved to `engine/` as internal extensions. No behaviour or public API change.

- [#338](https://github.com/softistx/nxgt-krepo/pull/338) [`b8306aa`](https://github.com/softistx/nxgt-krepo/commit/b8306aa88c76d06c96090c250cca34215e746cdb) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Internal: `JpaWorkflowStore.kt` and `MongoWorkflowStore.kt` are split by concern. The lease updates (`take`, `renew`, `release`) and the mapping between a `WorkflowRecord` and its row or document (encoding, decoding, `dueAt`, retention) moved to internal files beside each store. No behaviour or public API change.

- [#332](https://github.com/softistx/nxgt-krepo/pull/332) [`bdd375b`](https://github.com/softistx/nxgt-krepo/commit/bdd375b9a7230329217d9d37fb2022142009516f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Fixes a parent's unwind counting a `child` node as compensated when the child itself was left `Failed` (its own compensation failed). The parent now fails on that node with `ChildFailedException` and lands `Failed`; retry the child with `retryCompensation`, then the parent.

- [#343](https://github.com/softistx/nxgt-krepo/pull/343) [`2ed7ccc`](https://github.com/softistx/nxgt-krepo/commit/2ed7ccc53870c19e6fc3f9dfcb13495ec42aac6f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Fixes a parent's unwind recording a child as compensated when it was not. `undoChild` now accepts only a `Cancelled` or `Compensated` child: any other child (one another worker still holds, or one left `Failed`) fails the parent's node with a `ChildFailedException` whose `status` says which. The parent then lands `Failed` instead of `Compensated`: retry the parent once a held child is free, or retry a `Failed` child and then the parent. A node named `<context>` is now refused at build time, since the engine reserves that name.

## 0.4.0

### Minor Changes

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Adds `WorkflowEngine.retryCompensation(id)`: an instance left `Failed` because a compensation failed can now be reopened after the cause is fixed, and its unwind continues from the failed compensation without re-running the ones that succeeded. It refuses anything else with `WorkflowNotRetryableException`.

### Patch Changes

- Updated dependencies [[`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4)]:
  - stx-ktor@0.3.1

## 0.3.0

### Minor Changes

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `WorkflowWorker` takes its permit before launching, so a backlog no longer becomes one coroutine per id, skips ids already in flight, waits a poll instead of spinning on an instance of an unregistered workflow or one that keeps failing, and reports a failed `resume` through a new `onError` hook (default: a logged warning); `JpaWorkflowStore.create` now refuses a concurrent duplicate with the same `IllegalArgumentException` as the other stores.

### Patch Changes

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The server dependencies move to their latest patch and minor releases: Ktor 3.6.0, the MongoDB driver 5.13.0, Kafka clients 4.4.0, the RabbitMQ client 5.37.0, Hibernate Validator 9.1.5 and KotlinPoet 2.4.0. Lettuce stays at 7.7 and kotlinx.serialization at the toolchain's 1.11.0.

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - kotlin-reflect moves to 2.4.20, matching the Kotlin stdlib the toolchain ships, and classgraph (classpath scanning in stx-jpa) moves from 4.8 to 6.0.0, a major release of that dependency that needs JDK 25, so a consumer that calls classgraph 4 APIs directly has to move too.
- Updated dependencies [[`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f)]:
  - stx-common@0.3.0
  - stx-ktor@0.3.0

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-workflow`, `io.github.softistx:stx-workflow-db`, `io.github.softistx:stx-workflow-ktor` and `io.github.softistx:stx-workflow-spring` — the **stx-workflow** family. They carry one version between them and ship together.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
