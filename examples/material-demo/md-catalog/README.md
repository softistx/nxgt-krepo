# md-catalog

Everything the `stx-material` catalogue is: `MaterialDemo()`, the story registry, the three panes,
the knobs, and the twenty-eight stories themselves.

```bash
./kotlin test -m md-catalog
```

**[`examples/material-demo`](../README.md) is the README** — how the demo is laid out, how to add a
story, what the theme dials prove, and what the headless render tests catch. This module is the
`kmp/lib` on `[jvm, android]` that holds all of it; the two launchers beside it hold a window and an
activity and nothing else.

It depends on `stx-material` as a **published artifact**, not as a module — the rule every example
here follows. `stx-material` is the only multiplatform library, so this is also the only place where
the published Gradle metadata is doing variant resolution rather than handing over a single jar.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
