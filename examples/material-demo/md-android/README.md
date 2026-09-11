# md-android

The Android launcher: one `ComponentActivity` around `MaterialDemo()`, and a manifest.

```bash
./kotlin run -m md-android     # with a device or emulator connected
```

**[`examples/material-demo`](../README.md) is the README.** This module is about twenty lines and
contains no stories and no logic, for the same reason [`md-desktop`](../md-desktop/README.md) does
not: a module has exactly one product type. Everything lives in
[`md-catalog`](../md-catalog/README.md).

It is also where the platform difference the catalogue cares about shows up — wallpaper colours are
offered as a theme dial only on Android 12+, and the panes draw no scrollbar here, because a finger
already knows a list moves.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
