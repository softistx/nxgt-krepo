---
name: skill-from-docs
description: Build or refresh a docs-backed skill — fetch a documentation site into <skill>/references/ as markdown, then curate a SKILL.md over it. Use when adding a skill for a library or tool, or when cached docs went stale after a version bump.
---

# Skill from docs

Turns a documentation site into a skill: `scripts/fetch_docs.py` caches the pages, you write the curated layer on top. Every docs-backed skill in this repo (`kotlin-toolchain`, `ktorfit`) was built this way.

## Refreshing an existing skill

```bash
python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill ktorfit
python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill ktorfit --list        # dry run
python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill ktorfit --only requests
```

Refresh when the tool's version moves past the `version` in its `docs-source.json`, or when a cached page contradicts what the tool actually does. Files under `references/` are generated — fix the script, never the output.

## Adding a new skill

1. **Probe the site.** `curl -sI <base>llms.txt`, `<base>sitemap.xml`, and the `<meta name="generator">` tag. Note which discovery method works.
2. **Write `<skill>/docs-source.json`:**
   ```json
   {
     "title": "Ktorfit",
     "base": "https://foso.github.io/Ktorfit/",
     "exclude": ["^CHANGELOG/$", "^License/$"],
     "version": "",
     "last_sync": ""
   }
   ```
   `exclude` entries are **regexes matched against each page's path relative to `base`** — anchor them (`^responseconverter/$`) so dropping a stale top-level page doesn't also drop the current nested one. Exclude changelogs, licences, and superseded duplicates: they are pure token cost. `version` and `last_sync` are filled in by the script.

   Add `"discovery": "sitemap"` (or `"llms"` / `"nav"`) to pin one method instead of trying them in order. Pin it when a site serves a **full-text** `llms.txt` — the whole documentation inlined rather than a link index. kotlinlang.org does, and harvesting URLs out of it yielded 8 pages instead of 132, each carrying the trailing `)` of the sentence it was linked from.
3. **Fetch:** `fetch_docs.py --skill <name>`. Check the output: fences labelled, no site chrome, no navigation.
4. **Read the pages, then write `SKILL.md` by hand.** The cache is raw documentation; the skill body is the curated layer — the rules that are easy to get wrong plus a routing table into `references/`. Never paste doc prose into SKILL.md; point at the file instead.
5. **Verify load-bearing claims against the real tool** before writing them down. Build a throwaway project in the scratchpad and run the tool. Docs lag reality: this is what established that the Kotlin Toolchain ignores catalog `[bundles]`, and that Ktorfit needs no Gradle plugin here.
6. **Check the token budget** below, then commit both the skill and its cache.

## Token budget

Skills load in three tiers, and cost belongs in the deepest tier that can hold it.

| Tier | When it loads | Budget |
| --- | --- | --- |
| `description` | every session, for every skill | **≤ 250 chars.** What it is + when to use it, nothing else. |
| SKILL.md body | on activation | **≤ ~120 lines** (hard ceiling ~5k tokens). Decisions and gotchas, not documentation. |
| `references/` | only when a page is opened | unbounded, but each page must be worth opening |

`INDEX.md` is the routing table someone reads to *choose* a page, so it carries file and topic only — no URLs (each page's own first line has its source), no fetch dates (the header has the sync date), and a size hint only where it changes the decision. The script regenerates it from disk on every run, so a `--only` fetch never truncates the map.

## How the fetcher works

- **Discovery**, in order: `llms.txt`, `sitemap.xml`, then nav links off the base page.
- **Misconfigured sitemaps are rebased.** Ktorfit's `sitemap.xml` declares its `<loc>`s under `https://github.com/Foso/Ktorfit/` because its `site_url` points at the repo. The script takes each entry's path relative to the sitemap's own declared root and re-joins it onto the configured `base`, warning when the hosts differ.
- **Site chrome is stripped**: nav, MkDocs' `md-source-file`/`md-feedback`/`headerlink`, "Last update" stamps, and floated "Documentation issue? Report / edit" banners.
- **Code fences are inferred.** MkDocs Material sites commonly strip language classes from their HTML, so the fence language is guessed from content (yaml/bash/kotlin/toml/json). A mislabelled fence is cosmetic — don't hand-fix the output.
- Each page keeps a one-line provenance header (source URL, version, fetch date). Pages left over from an older version are marked stale in the index.
- `--delay` (default 0.3s) paces requests; be polite to docs hosts.

**Code blocks are not always `<pre>`.** Writerside sites (kotlinlang.org) render samples as a bare `<div class="code-block">` or `<div class="code-collapse">` with the language in `data-lang` and no `<pre>` on the page at all; the script treats those as fences. A site with yet another wrapper will flatten its samples into prose — that is the symptom to watch for on a first fetch, and the fix belongs in `code_block`.

Requires `python3` with `beautifulsoup4`. For a site the script can't crawl (auth, heavy JS), fall back to `WebFetch` per page and write `references/` by hand, keeping the same provenance header format.
