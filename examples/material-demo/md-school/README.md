# md-school

A school-management app built only on `stx-material`: dashboard, students (list and detail), grades
(table and form), timetable, messages and settings, with deterministic in-memory data.

```bash
./kotlin run -m md-desktop -- school
./kotlin test -m md-school -p jvm
```

**[`examples/material-demo`](../README.md) is the README** — what the app holds, how its screens
render headlessly, and how it is launched. This module is the `kmp/lib` on `[jvm, android]` that holds
all of it; `md-desktop` and `md-android` launch it with a window and an activity.

It depends on `stx-material` as a **published artifact**, like every example here, and on nothing
else of the owner's: any screen that needed something the library lacks would be a gap to report, not
a component to write here.

## The evaluation bench

One command renders every screen of the School app at {phone 412x915, tablet 1024x1366, desktop
1440x900} x {light, dark}, in the Loaded state, plus the student detail and a conversation thread:
8 views x 6 = 48 PNGs, a `manifest.json` (file, screen, viewport, theme, size) and one
`contact-<viewport>.png` per viewport (light and dark side by side, each labelled). Files are named
`<screen>-<viewport>-<light|dark>.png`.

```bash
SCHOOL_BENCH_DIR=/abs/dir ./kotlin test -m md-school -p jvm
# only some shots (comma-separated parts of a file name):
SCHOOL_BENCH_DIR=/abs/dir SCHOOL_BENCH_FILTER=dashboard,tablet-dark ./kotlin test -m md-school -p jvm
```

Without `SCHOOL_BENCH_DIR` the bench spec writes nothing. Score the result with
[`docs/design-grid.md`](docs/design-grid.md).

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
