---
"stx-material": patch
---

No change to the library itself. This is the first end-to-end run of the per-family release
circuit, cut deliberately on the family with **no dependents** so that it moves one version line
and publishes one batch. What it carries is the corrected note in the manifest and the README about
which command compiles the Apple targets: `./kotlin publish` cross-compiles both on Linux,
`./kotlin build` skips them.
