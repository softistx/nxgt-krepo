---
name: large-feature-branch-workflow
description: Two-level branching for work too large for one PR — develop → feature/<slug> → feat/<slug>-<task> — with dependency sequencing, per-branch verification, and a post-merge deep review. Use for multi-module or multi-session features, not single fixes.
---

# Large feature branch workflow

For a feature too large to land in one branch: several new modules, cross-cutting wiring, or work spanning multiple sessions. It keeps half-finished work off `develop` while still merging in reviewable chunks.

**Not for** an ordinary fix or a single-module change — those stay flat: one `fix/<slug>` or `feat/<slug>` off `develop`, one PR back into it.

## Structure

```
develop
  └─ feature/<slug>                  ← integration branch, off develop
       ├─ feat/<slug>-<task-1>       ← off feature/<slug>, not off develop
       ├─ feat/<slug>-<task-2>
       └─ feat/<slug>-<task-N>
```

- `feature/<slug>` never receives code directly — only merges from auxiliary branches via PR.
- Auxiliary branches deliver one self-contained, reviewable slice each. Use `fix/<slug>-<task>` when the slice repairs something introduced earlier in the same feature.
- PRs target `feature/<slug>` until the feature is complete; then one final PR `feature/<slug>` → `develop`.
- **`develop` is the terminus.** No branch here ever targets `main`, and finishing a feature does not touch it: `main` is aligned from `develop` separately, when someone asks for it.

## Sequencing

Order branches by dependency, and lead with a **scaffold branch** whenever later branches would otherwise touch the same shared files. In this repo that means the scaffold branch creates each module directory with its `module.yaml`, registers every module in `project.yaml`, and adds the catalog aliases the feature will need to `libs.versions.toml` — so later branches only add files inside their own module and conflicts stay additive.

Every auxiliary branch must leave `feature/<slug>` green before its PR opens:

```bash
kotlin show modules            # manifests parse (fails with a line pointer if not)
kotlin build
kotlin test
kotlin check
```

**Tests means write them, not just run what exists.** A branch that adds a module adds that module's tests under `test/` in the same PR. Never merge a branch that leaves the feature branch red.

## Post-merge deep review

The integration branch only proves the feature is green and internally consistent — that is not a code-quality review. When slices are built independently, cross-branch duplication and quietly diverging conventions are the expected outcome, not the edge case. So do this review for every feature built this way.

- **When:** after `feature/<slug>` → `develop` has merged, so the review reflects what actually shipped.
- **Where:** a fresh `review/<slug>-deep-audit` branch off `develop`, never the merged feature branch.
- **How:** review axes are bugs, tech debt, duplication, and cross-module consistency — especially whether every module solved the same class of problem the same way. On an explicit go-ahead to use subagents, split it: one reviewer per module group plus one scoped to shared wiring (`project.yaml`, `libs.versions.toml`, templates) and cross-module duplication, which is the perspective no single-module reviewer has. Without that go-ahead, one sequential pass over the same axes.
- **Output:** a findings report — file, one-line summary, severity — not edits. Default to report-only and let the user decide what to act on.

## Conventions

Commits follow the log's existing shape: `<type>: <Capitalized summary>`, where type is one of `feat`, `fix`, `update`, `docs`, `chore`, `refactor`, `tests`.

## Example

```
develop
  └─ feature/http-clients
       ├─ feat/http-scaffold        (module dirs, project.yaml, catalog aliases)
       ├─ feat/http-core            (libs/http-core: shared models + config)
       ├─ feat/http-ktorfit         (libs/http-ktorfit: API interfaces + KSP wiring)
       ├─ feat/http-auth            (libs/http-auth: token refresh, depends on core)
       └─ feat/http-integration     (end-to-end tests, final hardening)
```

Each PRs into `feature/http-clients`; once all are merged and the branch is verified end to end, one PR merges it into `develop`.
