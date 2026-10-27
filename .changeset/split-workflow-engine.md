---
"stx-workflow": patch
---

Internal: `WorkflowEngine.kt` is split by concern. `WorkflowEngineBuilder` has its own file, and the advancing machinery (`advance`, `signal` delivery, `startChild`) and the operator verbs' bodies (`undo`, `undoChild`, `retryCompensation`) moved to `engine/` as internal extensions. No behaviour or public API change.
