# Grok Build

Grok already loads the repo-root [AGENTS.md](../../AGENTS.md) and [CLAUDE.md](../../CLAUDE.md). This file is only the Grok-specific overlay.

Skills live in `.agents/skills/` (the cross-client Agent Skills convention). `.grok/skills` is a symlink to that directory, matching `.claude/skills`, so Grok's native scanner and Claude Code see the same files. Edit the real files under `.agents/skills/`.
