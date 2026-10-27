---
"stx-workflow": patch
---

Fixes a parent's unwind recording a child as compensated when it was not. `undoChild` now accepts only a `Cancelled` or `Compensated` child: one that another worker still holds fails the parent's node with a retryable `ChildFailedException`, and one left `Failed` fails it without a retry. The parent then lands `Failed` instead of `Compensated`, and `retryCompensation` finishes it once the child is free or retried.
