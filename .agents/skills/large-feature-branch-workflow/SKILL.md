---
name: large-feature-branch-workflow
description: How to land a feature too large for one PR — a series of feat/<slug>-<task> branches, each off develop and each PR'd back into develop — with dependency sequencing, per-slice verification, and a post-merge deep review. Use for multi-module or multi-session features, not single fixes.
---

# Large feature branch workflow

For a feature too large to land in one branch: several new modules, cross-cutting wiring, or work spanning multiple sessions. It splits the feature into slices that each land on `develop` on their own, so every one of them is reviewed, green and shippable rather than accumulating behind a branch nobody reads until the end.

**Not for** an ordinary fix or a single-module change — those are already one branch and one PR, and need no plan.

## Structure

```
develop
  ├─ feat/<slug>-<task-1>    ← off develop, PR → develop, merged before the next starts
  ├─ feat/<slug>-<task-2>    ← off the develop that already carries task-1
  └─ feat/<slug>-<task-N>
```

- **Every PR targets `develop`**: `gh pr create --base develop`. There is no integration branch, and a slice never PRs into another slice.
- **`develop` is the terminus.** No branch here ever targets `main`, and finishing a feature does not touch it: `main` is aligned from `develop` separately, when someone asks for it.
- Each slice branches off the `develop` that already has the previous one, so `git checkout develop && git pull --ff-only` before starting the next. Slices that genuinely do not touch each other can run in parallel; anything that shares a file should not.
- Use `fix/<slug>-<task>` when a slice repairs something an earlier slice of the same feature introduced.

## Every slice leaves `develop` shippable

This is the whole cost of merging early, and it is the rule that makes the rest work: a slice lands on `develop` before the feature is finished, so `develop` must be releasable at every step.

- **New capability lands dark.** Opt-in behind a property or a flag, or simply not referenced by anything the application loads, until the slice that turns it on. A module that exists, compiles and is wired to nothing is a fine intermediate state; a half-wired one is not.
- **No slice leaves a dangling reference** — no import of a class the next slice will add, no config key nothing reads, no test skipped "until later".
- **A slice that cannot land green is drawn wrong.** Redraw it — usually by moving the wiring into its own later slice — rather than reaching for a long-lived branch to hide it behind.

## Sequencing

Order slices by dependency, and lead with a **scaffold slice** whenever later ones would otherwise touch the same shared files. In this repo that means the scaffold creates each module directory with its `module.yaml`, registers every module in `project.yaml`, and adds the catalog aliases the feature will need to `libs.versions.toml` — so every later slice only adds files inside its own module.

Merging each slice before the next begins is what keeps that cheap: the next branch starts from a `develop` that already has the scaffold, so there is nothing to rebase and nothing to conflict.

Every slice must be green before its PR opens:

```bash
kotlin show modules            # manifests parse (fails with a line pointer if not)
kotlin build
kotlin test
kotlin check
```

**Tests means write them, not just run what exists.** A slice that adds a module adds that module's tests under `test/` in the same PR. Never merge a slice that leaves `develop` red.

## Post-merge deep review

Each PR proves its own slice is green; none of them proves the slices agree with each other. When slices are built one at a time, cross-slice duplication and quietly diverging conventions are the expected outcome, not the edge case. So do this review for every feature built this way.

- **When:** after the last slice has merged into `develop`, so the review reflects what actually shipped.
- **Where:** a fresh `review/<slug>-deep-audit` branch off `develop`.
- **How:** review axes are bugs, tech debt, duplication, and cross-module consistency — especially whether every slice solved the same class of problem the same way. On an explicit go-ahead to use subagents, split it: one reviewer per module group plus one scoped to shared wiring (`project.yaml`, `libs.versions.toml`, templates) and cross-module duplication, which is the perspective no single-module reviewer has. Without that go-ahead, one sequential pass over the same axes.
- **Output:** a findings report — file, one-line summary, severity — not edits. Default to report-only and let the user decide what to act on.

## Conventions

Commits follow the log's existing shape: `<type>: <Capitalized summary>`, where type is one of `feat`, `fix`, `update`, `docs`, `chore`, `refactor`, `tests`.

## Example

```
develop
  ├─ feat/http-scaffold        (module dirs, project.yaml, catalog aliases)
  ├─ feat/http-core            (libs/http-core: shared models + config)
  ├─ feat/http-ktorfit         (libs/http-ktorfit: API interfaces + KSP wiring)
  ├─ feat/http-auth            (libs/http-auth: token refresh, depends on core)
  └─ feat/http-integration     (end-to-end tests, final hardening)
```

Five branches, five PRs, all `--base develop`, each merged before the next is cut. The feature is
finished when the last one merges; then `review/http-clients-deep-audit` off `develop`.
