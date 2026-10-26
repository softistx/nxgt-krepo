# stx-material

## 0.2.3

### Patch Changes

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Moves the Compose dependencies stx-material exports to their stable lines: Navigation3 1.2.0
  (from 1.2.0-alpha02), Material 3 Adaptive 1.3.0 (from 1.3.0-beta02), and Coil 3.6.3.

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `Checkbox`, `Switch` and `RadioGroup` rows keep their 48 dp touch target and `Checkbox` applies the caller's modifier to its outermost node, and `ListTile`, `Breadcrumb`, `Stepper`, `Accordion` and `Disclosure` are announced as buttons and show Material's focus and press indication again.

## 0.2.2

### Patch Changes

- [#233](https://github.com/softistx/nxgt-krepo/pull/233) [`e4325bf`](https://github.com/softistx/nxgt-krepo/commit/e4325bfb4a3521a87a3129c88019913e83534c46) Thanks [@SteveGT96](https://github.com/SteveGT96)! - No change to the library itself. This is the first end-to-end run of the per-family release
  circuit, cut deliberately on the family with **no dependents** so that it moves one version line
  and publishes one batch. What it carries is the corrected note in the manifest and the README about
  which command compiles the Apple targets: `./kotlin publish` cross-compiles both on Linux,
  `./kotlin build` skips them.

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-material`.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
