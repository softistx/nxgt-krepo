---
"stx-workflow": minor
---

Adds `WorkflowEngine.retryCompensation(id)`: an instance left `Failed` because a compensation failed can now be reopened after the cause is fixed, and its unwind continues from the failed compensation without re-running the ones that succeeded. It refuses anything else with `WorkflowNotRetryableException`.
