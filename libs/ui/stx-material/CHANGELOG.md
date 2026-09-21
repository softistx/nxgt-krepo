# stx-material

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
