<!--
Base: develop. A PR opened against `main` has the wrong base — `main` is only ever aligned from
`develop`, never merged into. See CONTRIBUTING.md.
-->

## What this changes

<!-- One paragraph. What behaviour is different afterwards, and why. -->

## Before you ask for a review

- [ ] `./kotlin build` and `./kotlin test` pass
- [ ] `ktlint -F --relative "**/*.kt" "!build/**"` is clean (ktlint 1.8.0, same as CI)
- [ ] Touching `libs/`? `pnpm changeset` — CI fails this PR without one
- [ ] Touching `libs/`? `./kotlin publish mavenLocal -m <library> --non-transitive`, then build a
      consumer. The examples resolve published coordinates, so this is what proves the POM names
      what a consumer needs
- [ ] Documentation changed in this PR, not a later one — see the audience table in AGENTS.md for
      which file owns what
