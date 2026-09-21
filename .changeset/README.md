# Changesets

Every pull request that touches `libs/` declares what it does to the published artifacts:

```bash
bun changeset
```

Pick `patch`, `minor` or `major`, **pick the families your change affects**, write one sentence a
*reader* will see in the release notes — not a restatement of the commit subject — and commit the
`.changeset/*.md` file it writes. CI fails a PR that touches `libs/` without one.

If your change genuinely publishes nothing different — a spec, a comment, a harness timeout — say so
explicitly rather than inventing a bump:

```bash
bun changeset --empty
```

## One version per family

A **family** is a directory `libs/<role>/<name>/`: the library and its framework integrations.
`stx-jpa`, `stx-jpa-ktor` and `stx-jpa-spring` are three artifacts and one release line — they ship
together and are tested together, so they carry one version between them. There are 17 of them,
covering 45 artifacts, and each has a `package.json` that Changesets versions and a
`<name>.module-template.yaml` that carries that version into the build.

A changeset therefore names a family, never an artifact and never the repository:

```markdown
---
"stx-graphix": minor
---

Exception handlers that reach the client.
```

## What a bump drags with it

Changesets bumps the families you named **and the families that depend on them at runtime**, because
a published POM names its dependencies at an exact version — `stx-jpa`'s POM says
`io.github.softistx:stx-common:0.2.1` — so a dependent really does have to be republished.

```
stx-common  ←  13 families
stx-ktor    ←  10
stx-i18n    ←  stx-spring-boot
stx-testing, stx-openapi-generator, stx-material  ←  nothing
```

`compile-only` and `test-dependencies` are deliberately **not** in that graph. The first is
`provided` in the POM and the second is not in the POM at all, so neither obliges anyone to
republish. That is why a change to `stx-testing` — which 20 modules use in their specs — moves
nothing at all.

That graph lives in the families' `package.json` files, and `bun scripts/graph.ts --check` derives
it again from every `module.yaml` and fails if the two disagree — so **adding a `//libs/...`
dependency across families means editing that family's `package.json` in the same change**.
`bun scripts/graph.ts` prints it.

**A `major` must say what happens to its dependents, and CI enforces it.** Changesets only ever
gives a dependent a `patch`, whatever the dependency did. So a `major` on `stx-common` would leave
`stx-jpa` on a patch bump whose POM points at a new major — a consumer taking that patch gets the
breaking change transitively, which is the one thing a patch promises not to do.

So the guard fails the pull request, names every dependent, and prints the lines to paste:

```
"stx-jpa": major
"stx-mongo": major
```

If the break genuinely cannot reach a consumer of a dependent — it is behind a type that dependent
never exposes — say so in the changeset's prose instead, naming each one:

```markdown
---
"stx-common": major
---

Reworked the paging cursor. The two callers below never expose it.

unaffected: stx-redis, stx-storage
```

A name on that line that depends on no `major` in the changeset is refused, because it is a typo in
the name it was meant to excuse and would otherwise excuse nothing silently. The rule is an error
and not a warning on purpose: the cost is paid by a consumer who is not in the room, and a warning
on a rare event is one people learn to scroll past.

## What happens next

`changeset version` bumps each affected `package.json`, writes that family's `CHANGELOG.md`, and
runs `scripts/sync-version.ts` to carry the new versions into the two files the Kotlin build reads —
the family's own template and its key in `libs.versions.toml`. CI opens that as a
"Version Packages" pull request against `develop`. Merging it is the decision to release: the same
workflow then publishes **only the families whose version has moved**, tags each one
`<family>@<version>`, and cuts a release.

[`docs/releasing.md`](../docs/releasing.md) has the whole circuit;
[`docs/consuming.md`](../docs/consuming.md) has the consumer's half of it.
