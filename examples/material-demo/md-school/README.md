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

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
