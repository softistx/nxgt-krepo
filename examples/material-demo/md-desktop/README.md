# md-desktop

The desktop launcher: a `Window` around `MaterialDemo()` (or `SchoolApp()` with `-- school`), and a `main`.

```bash
./kotlin run -m md-desktop              # the catalogue, in a window sized for the three-pane layout
./kotlin run -m md-desktop -- school    # the School showcase app ([`md-school`](../md-school/README.md))
```

**[`examples/material-demo`](../README.md) is the README.** This module is about twenty lines and
contains no stories and no logic — a module has exactly one product type, so `jvm/app` and
`android/app` cannot be the same one, which is the only reason the demo is three modules instead of
one. Everything lives in [`md-catalog`](../md-catalog/README.md). If this file grows, something is
in the wrong module.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
