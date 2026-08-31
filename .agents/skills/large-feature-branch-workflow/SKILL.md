---
name: large-feature-branch-workflow
description: How to land a feature too large for one PR — an integration branch off develop, a series of feat/<slug>-<task> slices each PR'd into it, then one PR from the integration branch to develop — with dependency sequencing, per-slice verification, and a deep review before the develop PR. Use for multi-module or multi-session features, not single fixes.
---

# Large feature branch workflow

For a feature too large to land in one branch: several new modules, cross-cutting wiring, or work spanning multiple sessions. It splits the feature into slices that are each reviewed and green on their own, collects them on one integration branch, and lands the whole feature on `develop` as a single reviewable unit.

**Not for** an ordinary fix or a single-module change — those are already one branch and one PR, and need no plan.

## Structure

```
develop
  └─ feat/<slug>                  ← integration branch, off develop
       ├─ feat/<slug>-<task-1>    ← off the integration branch, PR → feat/<slug>, merged before the next starts
       ├─ feat/<slug>-<task-2>    ← off the integration branch that already carries task-1
       └─ feat/<slug>-<task-N>
```

- **Cut the integration branch first**: `git checkout develop && git pull --ff-only && git checkout -b feat/<slug>`, and push it so the slice PRs have a base to target.
- **Every slice PR targets the integration branch**: `gh pr create --base feat/<slug>`. A slice never PRs into another slice, and never into `develop`.
- **The integration branch is the only thing that PRs into `develop`**, once every slice is merged and the whole feature is green: `gh pr create --base develop`. Say so and wait for a go-ahead before merging it — that PR is the feature, and it is the one a human wants to look at.
- **`develop` is the terminus.** No branch here ever targets `main`, and finishing a feature does not touch it: `main` is aligned from `develop` separately, when someone asks for it.
- Each slice branches off the integration branch that already has the previous one, so `git checkout feat/<slug> && git pull --ff-only` before starting the next. Slices that genuinely do not touch each other can run in parallel; anything that shares a file should not.
- Use `fix/<slug>-<task>` when a slice repairs something an earlier slice of the same feature introduced.

## Every slice leaves the integration branch green

This is the rule that makes the rest work: a slice merges before the feature is finished, so the integration branch must build and test clean at every step, and the next slice starts from something that works.

- **New capability lands dark.** Opt-in behind a property or a flag, or simply not referenced by anything the application loads, until the slice that turns it on. A module that exists, compiles and is wired to nothing is a fine intermediate state; a half-wired one is not.
- **No slice leaves a dangling reference** — no import of a class the next slice will add, no config key nothing reads, no test skipped "until later".
- **A slice that cannot land green is drawn wrong.** Redraw it — usually by moving the wiring into its own later slice — rather than letting the integration branch sit red.

The *shippable* bar — that `develop` could be released as it stands — is what the integration PR has to clear, not each slice. That is the whole reason the integration branch exists: a feature can be assembled across several green steps without any half-finished step reaching `develop`.

## Sequencing

Order slices by dependency, and lead with a **scaffold slice** whenever later ones would otherwise touch the same shared files. In this repo that means the scaffold creates each module directory with its `module.yaml`, registers every module in `project.yaml`, and adds the catalog aliases the feature will need to `libs.versions.toml` — so every later slice only adds files inside its own module.

Merging each slice before the next begins is what keeps that cheap: the next branch starts from an integration branch that already has the scaffold, so there is nothing to rebase and nothing to conflict.

Every slice must be green before its PR opens:

```bash
kotlin show modules            # manifests parse (fails with a line pointer if not)
kotlin build
kotlin test
kotlin check
```

**Tests means write them, not just run what exists.** A slice that adds a module adds that module's tests under `test/` in the same PR. Never merge a slice that leaves the integration branch red.

## Deep review before the develop PR

Each PR proves its own slice is green; none of them proves the slices agree with each other. When slices are built one at a time, cross-slice duplication and quietly diverging conventions are the expected outcome, not the edge case. So do this review for every feature built this way.

- **When:** after the last slice has merged into the integration branch and **before** the PR to `develop` opens, so a finding can still land as one more slice instead of as a follow-up on `develop`.
- **Where:** a fresh `review/<slug>-deep-audit` branch off the integration branch.
- **How:** review axes are bugs, tech debt, duplication, and cross-module consistency — especially whether every slice solved the same class of problem the same way. On an explicit go-ahead to use subagents, split it: one reviewer per module group plus one scoped to shared wiring (`project.yaml`, `libs.versions.toml`, templates) and cross-module duplication, which is the perspective no single-module reviewer has. Without that go-ahead, one sequential pass over the same axes.
- **Output:** a findings report — file, one-line summary, severity — not edits. Default to report-only and let the user decide what to act on.

## Conventions

Commits follow the log's existing shape: `<type>: <Capitalized summary>`, where type is one of `feat`, `fix`, `update`, `docs`, `chore`, `refactor`, `tests`.

## Example

```
develop
  └─ feat/http-clients                (integration branch)
       ├─ feat/http-scaffold          (module dirs, project.yaml, catalog aliases)
       ├─ feat/http-core              (libs/http-core: shared models + config)
       ├─ feat/http-ktorfit           (libs/http-ktorfit: API interfaces + KSP wiring)
       ├─ feat/http-auth              (libs/http-auth: token refresh, depends on core)
       └─ feat/http-integration       (end-to-end tests, final hardening)
```

Five slice branches, five PRs, all `--base feat/http-clients`, each merged before the next is cut.
Then `review/http-clients-deep-audit` off the integration branch, and finally one PR
`feat/http-clients` → `develop` — the feature, in one place, waiting on a go-ahead.
