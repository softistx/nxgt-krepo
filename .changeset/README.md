# Changesets

Every pull request that touches `libs/` declares what it does to the published artifacts:

```bash
bun changeset
```

Pick `patch`, `minor` or `major`, write one sentence a *reader* will see in the release notes —
not a restatement of the commit subject — and commit the `.changeset/*.md` file it writes. CI fails
a PR that touches `libs/` without one.

## Why the package is called `nxgt-krepo`

There is one "package" here and it is the repository itself, declared private in the root
`package.json`. It stands for the whole `io.github.softistx:stx-*` line, which releases in lockstep:

- the toolchain cannot override `settings.publishing.version` from the command line, so the version
  is a literal line in `publishing.module-template.yaml`, shared by all 46 libraries;
- publishing is all-or-nothing across a dependency chain — `stx-jpa` depends on `stx-common`, so
  bumping one republishes the other regardless;
- the 19 `$libs.stx.*` catalog aliases resolve a single `stx = "…"`, which is what lets the examples
  consume published artifacts instead of module paths.

So a changeset's `minor` is a minor for the whole line. Name the affected library in the prose
instead — `stx-graphix: …` — the way the commit subjects already do.

## What happens next

`changeset version` bumps `package.json`, writes `CHANGELOG.md`, and runs `scripts/sync-version.mjs`
to carry the new version into the two files the Kotlin build reads. CI opens that as a
"Version Packages" pull request against `develop`. Merging it is the decision to release: the same
workflow then tags `vX.Y.Z` and publishes the 46 artifacts to Maven Central.

`docs/releasing.md` has the whole circuit.
