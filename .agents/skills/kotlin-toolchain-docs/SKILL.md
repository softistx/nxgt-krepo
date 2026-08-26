---
name: kotlin-toolchain-docs
description: Fetch the official Kotlin Toolchain documentation from https://kotlin-toolchain.org and refresh or generate the skills built from it. Use when the toolchain is upgraded, when a cached reference looks stale or contradicts observed CLI behavior, when a docs page is missing from the cache, or when asked to add/update a Kotlin Toolchain skill.
---

# Kotlin Toolchain docs sync

Keeps `.agents/skills/kotlin-toolchain/references/` in sync with the official docs at <https://kotlin-toolchain.org/>, and is the entry point for generating further skills from those docs.

## Refresh the cache

```bash
python3 .agents/skills/kotlin-toolchain-docs/scripts/sync_docs.py            # all pages, latest docs version
python3 .agents/skills/kotlin-toolchain-docs/scripts/sync_docs.py --list     # list doc URLs, fetch nothing
python3 .agents/skills/kotlin-toolchain-docs/scripts/sync_docs.py --only user-guide/plugins   # subset (repeatable)
python3 .agents/skills/kotlin-toolchain-docs/scripts/sync_docs.py --version 0.12 --out DIR
```

Each page is written to `references/<slug>.md` with a provenance comment naming its source URL, docs version, and fetch date; `references/INDEX.md` is regenerated as the map of file → topic → URL. Requires `python3` with `beautifulsoup4`. Files under `references/` are generated — never hand-edit them, change the script and re-run.

## When to run it

- After `kotlin update` or any toolchain version bump — compare `kotlin -v` against the version recorded in `references/INDEX.md`. If they differ, re-sync; the schema does change between 0.x releases.
- When a cached page contradicts what the CLI actually does. The CLI wins: re-sync, and if the docs still disagree, record the observed behavior in `.agents/skills/kotlin-toolchain/SKILL.md` with the command that demonstrates it.
- When a topic is missing from `references/INDEX.md` — the sitemap gained pages.

## Site facts worth knowing

- `https://kotlin-toolchain.org/` redirects to `/latest/`, which serves the concrete version (`/0.12/...`). The sitemap lives at `/latest/sitemap.xml` and lists every page; there is **no** `llms.txt` and no raw `.md` endpoint.
- The site is MkDocs Material and **strips language classes from code blocks**, so the script infers fences (`yaml`/`bash`/`kotlin`/`toml`) heuristically. A mislabeled fence is cosmetic; don't "fix" it by editing the generated file.
- Be polite: the script sleeps between requests (`--delay`).

## Updating or generating skills from the docs

Prefer *one* durable skill (`kotlin-toolchain`) plus the cached references over many thin skills. Add a new skill only for a topic with its own workflow and its own commands — writing toolchain plugins for `plugins/`, or a Gradle→toolchain migration, are the plausible candidates.

When updating `kotlin-toolchain/SKILL.md` from a fresh sync:

1. Re-read the pages that back its claims: `user-guide-basics.md`, `reference-project.md`, `reference-module.md`, `user-guide-dependencies.md`, `user-guide-templates.md`, `user-guide-testing.md`.
2. Keep SKILL.md short and decision-shaped — the rules that are easy to get wrong, plus the pointer table into `references/`. Bulk documentation belongs in `references/`, not in the skill body.
3. **Verify anything load-bearing against the real CLI before writing it down.** Build a throwaway project in the session scratchpad (never inside `libs/` or `plugins/`) and check the resolved model:

   ```bash
   mkdir -p "$SCRATCH/probe/libs/core/src" && cd "$SCRATCH/probe"
   printf 'modules:\n  - libs/core\n' > project.yaml
   printf 'product: jvm/lib\n\ndependencies:\n  - $libs.some.alias\n' > libs/core/module.yaml
   kotlin show modules && kotlin show dependencies -m core
   ```

   `kotlin show dependencies` proves a dependency actually resolves, `kotlin show settings` proves how templates merge, and an unknown manifest key fails with a line pointer — that is how the `[bundles]` gap and the `//module` path rule in `kotlin-toolchain/SKILL.md` were established.
4. Record the docs version the claims came from, so the next sync can tell what needs re-checking.
