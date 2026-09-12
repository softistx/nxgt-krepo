---
"nxgt-krepo": patch
---

The libraries now live in role folders — `libs/core`, `libs/data`, `libs/messaging`, `libs/api`, `libs/observability`, `libs/ui` — so that ownership is readable from the tree. No coordinate changes: every artifact keeps its `io.github.softistx:stx-*` name, because a module's name is still its own directory name.
