# md-desktop

The desktop launcher: a `Window` around `MaterialDemo()`, and a `main`.

```bash
./kotlin run -m md-desktop     # a window, sized for the three-pane layout
```

**[`examples/material-demo`](../README.md) is the README.** This module is about twenty lines and
contains no stories and no logic — a module has exactly one product type, so `jvm/app` and
`android/app` cannot be the same one, which is the only reason the demo is three modules instead of
one. Everything lives in [`md-catalog`](../md-catalog/README.md). If this file grows, something is
in the wrong module.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
