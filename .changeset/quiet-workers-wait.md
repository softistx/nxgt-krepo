---
"stx-workflow": patch
---

`WorkflowWorker` takes its permit before launching, so a backlog no longer becomes one coroutine per id, skips ids already in flight, waits a poll instead of spinning on an instance of an unregistered workflow or one that keeps failing, and reports a failed `resume` through a new `onError` hook (default: a logged warning); `JpaWorkflowStore.create` now refuses a concurrent duplicate with the same `IllegalArgumentException` as the other stores.
