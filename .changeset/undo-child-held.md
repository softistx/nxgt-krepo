---
"stx-workflow": patch
---

Fixes a parent's unwind recording a child as compensated when it was not. `undoChild` now accepts only a `Cancelled` or `Compensated` child: any other child (one another worker still holds, or one left `Failed`) fails the parent's node with a `ChildFailedException` whose `status` says which. The parent then lands `Failed` instead of `Compensated`: retry the parent once a held child is free, or retry a `Failed` child and then the parent. A node named `<context>` is now refused at build time, since the engine reserves that name.
