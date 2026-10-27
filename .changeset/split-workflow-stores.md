---
"stx-workflow": patch
---

Internal: `JpaWorkflowStore.kt` and `MongoWorkflowStore.kt` are split by concern. The lease updates (`take`, `renew`, `release`) and the mapping between a `WorkflowRecord` and its row or document (encoding, decoding, `dueAt`, retention) moved to internal files beside each store. No behaviour or public API change.
