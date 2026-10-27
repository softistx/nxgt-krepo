#!/usr/bin/env python3
"""Fetch a documentation site and cache it as markdown inside a skill.

Each docs-backed skill owns a `docs-source.json` describing where its docs come
from; this script reads it, discovers the pages, converts the HTML to markdown,
and writes them to `<skill>/references/` with a regenerated INDEX.md.

Usage:
    fetch_docs.py --skill <name> [--only SUBSTR ...] [--list] [--delay SECONDS]
    fetch_docs.py --base URL --out DIR [...]      # ad-hoc, no config file
"""
from __future__ import annotations

import argparse
import datetime as _dt
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import urlparse

# The sibling modules sit beside this file; put the folder on the path so they import
# the same way under `python3 -I` (which drops the script directory) as under plain `python3`.
sys.path.insert(0, str(Path(__file__).resolve().parent))

import conversion  # noqa: E402
from conversion import page_to_markdown  # noqa: E402
from discovery import discover, get  # noqa: E402
from indexing import slug_for, write_index  # noqa: E402

SKILLS_DIR = Path(__file__).resolve().parents[2]
CONFIG_NAME = "docs-source.json"


def load_config(skill: str) -> tuple[Path, dict]:
    skill_dir = SKILLS_DIR / skill
    config_path = skill_dir / CONFIG_NAME
    if not config_path.is_file():
        raise SystemExit(f"no {CONFIG_NAME} in {skill_dir} — create one before fetching")
    return skill_dir, json.loads(config_path.read_text(encoding="utf-8"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--skill", help=f"skill directory under {SKILLS_DIR} holding {CONFIG_NAME}")
    ap.add_argument("--base", help="docs base URL (ad-hoc mode, overrides the config)")
    ap.add_argument("--out", help="output directory (ad-hoc mode)")
    ap.add_argument("--only", action="append", default=[], help="only fetch URLs containing this substring")
    ap.add_argument("--list", action="store_true", help="list the pages that would be fetched, then exit")
    ap.add_argument("--delay", type=float, default=0.3, help="seconds between requests")
    args = ap.parse_args()

    if not args.skill and not (args.base and args.out):
        ap.error("pass --skill, or both --base and --out")

    skill_dir, cfg = (None, {})
    if args.skill:
        skill_dir, cfg = load_config(args.skill)
    base = args.base or cfg.get("base")
    if not base:
        ap.error("no base URL: set it in the config or pass --base")
    if not base.endswith("/"):
        base += "/"
    out = Path(args.out) if args.out else skill_dir / "references"

    conversion.SITE_ORIGIN = "{0.scheme}://{0.netloc}".format(urlparse(base))

    urls, root = discover(base, cfg.get("sitemap"), cfg.get("discovery", "auto"), cfg.get("pages"))
    if not urls:
        print("no pages discovered", file=sys.stderr)
        return 1

    # `exclude` entries are regexes matched against each page's path relative to
    # the base, so a stale top-level copy can be dropped without also dropping
    # the current nested page of the same name.
    for pattern in cfg.get("exclude", []):
        urls = [u for u in urls if not re.search(pattern, u[len(base):] if u.startswith(base) else u)]
    if args.only:
        urls = [u for u in urls if any(s in u for s in args.only)]
    if args.list:
        print("\n".join(urls))
        return 0

    version = cfg.get("version") or ""
    if not version:
        m = re.search(r"/(\d+[\w.]*)/$", root)
        version = m.group(1) if m else ""

    out.mkdir(parents=True, exist_ok=True)
    stamp = _dt.date.today().isoformat()
    fetched = 0

    slugs: dict[str, str] = {}
    for url in urls:
        slug = slug_for(url, root)
        if slug in slugs:
            raise SystemExit(f"two pages share the file name {slug}.md: {slugs[slug]} and {url}")
        slugs[slug] = url

    for url in urls:
        slug = slug_for(url, root)
        try:
            title, body = page_to_markdown(get(url))
        except Exception as exc:  # noqa: BLE001 - report and keep going
            print(f"FAIL {url}: {exc}", file=sys.stderr)
            continue
        header = f"<!-- Generated from {url} (v{version}) on {stamp}. Do not edit; re-run fetch_docs.py. -->\n\n"
        (out / f"{slug}.md").write_text(header + (body or f"# {title}\n"), encoding="utf-8")
        fetched += 1
        print(f"ok  {slug}.md  <- {url}")
        time.sleep(args.delay)

    write_index(out, cfg, base, version, stamp)

    if skill_dir and fetched:
        cfg["version"], cfg["last_sync"] = version, stamp
        (skill_dir / CONFIG_NAME).write_text(json.dumps(cfg, indent=2) + "\n", encoding="utf-8")

    print(f"\nwrote {fetched} page(s) + INDEX.md to {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
