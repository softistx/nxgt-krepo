---
"stx-workflow": patch
---

`retryCompensation` now recovers an instance the engine set to `Failed` because its stored context no longer decoded. Once the context decodes again (the stored context repaired, or the definition fixed), it runs the instance's unwind; for a child, retry the child and then its parent. While the context still does not decode, the call keeps refusing with `WorkflowNotRetryableException`.
