---
"stx-workflow": patch
---

Fixes a parent's unwind counting a `child` node as compensated when the child itself was left `Failed` (its own compensation failed). The parent now fails on that node with `ChildFailedException` and lands `Failed`; retry the child with `retryCompensation`, then the parent.
