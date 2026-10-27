"""Name cached pages (slugs) and rebuild INDEX.md from them."""
from __future__ import annotations

import re
from pathlib import Path
from urllib.parse import urlparse

SIZE_HINT_BYTES = 8192


def slug_for(url: str, root: str) -> str:
    """The file name (without `.md`) a page is cached under; a trailing `.html` is dropped."""
    if not url.startswith(root):
        # A listed page from another site: its last path segment names it.
        path = urlparse(url).path.strip("/").rsplit("/", 1)[-1]
    else:
        path = url[len(root):].strip("/").replace("/", "-")
    return (re.sub(r"\.html?$", "", path) or "home").lower()


def write_index(out: Path, cfg: dict, base: str, version: str, stamp: str) -> None:
    """Rebuild INDEX.md from every cached page, so a partial fetch keeps the full map.

    Deliberately lean: this table is read to *choose* a page, so it carries file
    and topic only. Page URLs are derivable from the base plus each file's own
    provenance header, and a size hint appears only where it changes the decision.
    """
    rows = []
    for path in sorted(out.glob("*.md")):
        if path.name == "INDEX.md":
            continue
        text = path.read_text(encoding="utf-8")
        m = re.search(r"<!-- Generated from (\S+) \(v([^)]*)\)", text)
        page_version = m.group(2) if m else ""
        title = next((ln[2:].strip() for ln in text.splitlines() if ln.startswith("# ")), path.stem)
        notes = []
        if version and page_version and page_version != version:
            notes.append(f"stale: v{page_version}")
        size = path.stat().st_size
        if size > SIZE_HINT_BYTES:
            notes.append(f"{size // 1024}KB")
        suffix = f" _({', '.join(notes)})_" if notes else ""
        rows.append(f"| `{path.name}` | {title}{suffix} |")

    label = cfg.get("title") or out.parent.name
    versioned = f" — version **{version}**" if version else ""
    (out / "INDEX.md").write_text(
        "\n".join(
            [
                f"# {label} documentation cache",
                "",
                f"Source: <{base}>{versioned}, last sync **{stamp}**.",
                f"Refresh: `python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill {out.parent.name}`",
                "Each page's own source URL is in its first line. Generated — do not hand-edit.",
                "",
                "| File | Topic |",
                "| --- | --- |",
                *rows,
                "",
            ]
        ),
        encoding="utf-8",
    )
