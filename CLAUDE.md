# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Read [AGENTS.md](AGENTS.md) first** — it holds the shared build commands, module layout, and catalog conventions. The notes below are Claude Code specific.

Skills live in `.agents/skills/` (the cross-client Agent Skills convention); `.claude/skills` is a symlink to that directory, so both this repo's other agents and Claude Code load the same files. Edit the real files under `.agents/skills/`.

## Skills

- **`kotlin-toolchain`** before writing or debugging a `module.yaml`/`project.yaml`, adding a dependency, or wiring a module; **`ktorfit`** for Ktorfit API interfaces. Both carry a `references/` cache of their official docs — read the relevant page rather than answering from memory, since both move faster than memory does.
- **`skill-from-docs`** to add a skill for another library or tool, or to refresh a cached one. It owns the token budget rules that every skill here follows.
- **`large-feature-branch-workflow`** when a feature needs more than one PR.

## Working here

- Prefer `kotlin show modules` / `show settings -m <module>` / `show dependencies -m <module>` over reading manifests and inferring. They resolve the real model in seconds and surface manifest errors with a line pointer, without a compile.
- Verify load-bearing toolchain claims against the CLI before writing them into docs or skills. Build a throwaway project in the session scratchpad — never inside `libs/` or `plugins/` — and confirm with `kotlin show`.
- Give build and test commands a generous timeout. The first `kotlin build`/`kotlin test` after a toolchain change downloads the compiler, a JRE, and dependencies into `~/.cache/JetBrains/Kotlin`, which can far exceed the default two-minute Bash timeout.
- Never introduce Gradle files to "fix" a build. If something needs a build feature this repo lacks, it belongs in a toolchain plugin module under `plugins/`, not in a `build.gradle.kts`.
- Add dependencies by adding a catalog alias to `libs.versions.toml` and referencing `$libs.<alias>` from the module — never paste raw versioned coordinates into a `module.yaml`, and remember `$libs.bundles.*` does not work here.
