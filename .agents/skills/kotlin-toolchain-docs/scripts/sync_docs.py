#!/usr/bin/env python3
"""Fetch the official Kotlin Toolchain docs and cache them as markdown.

Source: https://kotlin-toolchain.org/ (MkDocs Material, no raw .md endpoints,
so pages are fetched as HTML and converted here).

Usage:
    sync_docs.py [--version latest|0.12|...] [--out DIR] [--only SUBSTR ...] [--list]
"""
from __future__ import annotations

import argparse
import datetime as _dt
import re
import sys
import time
import urllib.request
from pathlib import Path
from xml.etree import ElementTree

from bs4 import BeautifulSoup, NavigableString, Tag

BASE = "https://kotlin-toolchain.org"
UA = {"User-Agent": "nxgt-krepo-docs-sync/1.0"}
SKIP_CLASSES = {"md-source-file", "md-feedback", "headerlink"}


def get(url: str) -> str:
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8", "replace")


def sitemap_urls(version: str) -> list[str]:
    xml = get(f"{BASE}/{version}/sitemap.xml")
    root = ElementTree.fromstring(xml)
    ns = {"s": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    return sorted(loc.text.strip() for loc in root.findall(".//s:loc", ns) if loc.text)


def slug_for(url: str, version: str) -> str:
    path = url.replace(f"{BASE}/", "").rstrip("/")
    path = re.sub(rf"^{re.escape(version)}/?", "", path)
    return (path.replace("/", "-") or "home").lower()


# --- minimal HTML -> markdown -------------------------------------------------

def esc(text: str) -> str:
    return re.sub(r"([*_`\[\]])", r"\\\1", text)


def inline(node, in_code: bool = False) -> str:
    if isinstance(node, NavigableString):
        text = re.sub(r"\s+", " ", str(node))
        return text if in_code else esc(text)
    if not isinstance(node, Tag):
        return ""
    if node.name in {"script", "style"}:
        return ""
    if set(node.get("class", [])) & SKIP_CLASSES:
        return ""
    if node.name == "pre":
        # a code block nested inside prose still deserves a real fence
        return "\n\n" + code_block(node) + "\n\n"
    kids = "".join(inline(c, in_code) for c in node.children)
    if node.name == "code" and not in_code:
        return f"`{kids.strip()}`" if kids.strip() else ""
    if node.name in {"strong", "b"}:
        return f"**{kids.strip()}**" if kids.strip() else ""
    if node.name in {"em", "i"}:
        return f"*{kids.strip()}*" if kids.strip() else ""
    if node.name == "br":
        return "\n"
    if node.name == "a":
        href = node.get("href", "")
        label = kids.strip()
        if not label:
            return ""
        if href.startswith("#") or not href:
            return label
        if href.startswith("/"):
            href = BASE + href
        return f"[{label}]({href})"
    return kids


def guess_lang(text: str) -> str:
    """The docs site strips language classes from its HTML, so infer the fence."""
    head = text.lstrip()
    if re.search(r"^\s*(product|modules|dependencies|test-dependencies|settings|repositories|apply|module|aliases):", text, re.M):
        return "yaml"
    if re.match(r"^(\$ |\./kotlin\b|kotlin \w|amper\b|curl |brew |sh )", head) or re.search(r"^\s*(\./)?kotlin (build|test|run|check|publish|clean|show|init|update|tool|package)\b", text, re.M):
        return "bash"
    if re.search(r"^\s*(package |import |fun |val |var |class |object |@)", text, re.M):
        return "kotlin"
    if re.search(r"^\s*\[(versions|libraries|bundles|plugins)\]", text, re.M):
        return "toml"
    if head.startswith("{") or head.startswith("["):
        return "json"
    return ""


def code_block(pre: Tag) -> str:
    code = pre.find("code") or pre
    lang = ""
    # mkdocs-material puts the language on the <code>, the <pre>, or a wrapping
    # <div class="language-yaml highlight"> / tabbed container.
    classes: list[str] = []
    for node in (code, pre, pre.parent, getattr(pre.parent, "parent", None)):
        if isinstance(node, Tag):
            classes += node.get("class", [])
    for cls in classes:
        m = re.match(r"(?:language-|highlight-)([\w+-]+)$", cls)
        if m and m.group(1) not in {"hl", "highlight", "text"}:
            lang = m.group(1)
            break
    text = code.get_text()
    text = "\n".join(line.rstrip() for line in text.split("\n")).strip("\n")
    return f"```{lang or guess_lang(text)}\n{text}\n```"


def table(tbl: Tag) -> str:
    rows = []
    for tr in tbl.find_all("tr"):
        cells = [inline(td).strip().replace("\n", " ") or " " for td in tr.find_all(["th", "td"])]
        if cells:
            rows.append(cells)
    if not rows:
        return ""
    width = max(len(r) for r in rows)
    rows = [r + [" "] * (width - len(r)) for r in rows]
    out = ["| " + " | ".join(rows[0]) + " |", "| " + " | ".join(["---"] * width) + " |"]
    out += ["| " + " | ".join(r) + " |" for r in rows[1:]]
    return "\n".join(out)


def block(node, depth: int = 0) -> str:
    if isinstance(node, NavigableString):
        text = re.sub(r"\s+", " ", str(node)).strip()
        return esc(text)
    if not isinstance(node, Tag) or node.name in {"script", "style", "nav"}:
        return ""
    classes = set(node.get("class", []))
    if classes & SKIP_CLASSES:
        return ""
    if node.name == "pre":
        return code_block(node)
    if node.name == "table":
        return table(node)
    if re.fullmatch(r"h[1-6]", node.name or ""):
        level = int(node.name[1])
        return "#" * level + " " + inline(node).strip()
    if node.name in {"ul", "ol"}:
        items = []
        for i, li in enumerate(node.find_all("li", recursive=False), 1):
            marker = f"{i}." if node.name == "ol" else "-"
            nested = [c for c in li.find_all(["ul", "ol", "pre"], recursive=False)]
            for n in nested:
                n.extract()
            head = inline(li).strip()
            pad = "  " * depth
            chunk = f"{pad}{marker} {head}" if head else ""
            for n in nested:
                body = block(n, depth + 1)
                if not body:
                    continue
                if n.name == "pre":
                    body = "\n".join("  " * (depth + 1) + ln for ln in body.split("\n"))
                chunk = f"{chunk}\n{body}" if chunk else body
            if chunk:
                items.append(chunk)
        return "\n".join(items)
    if node.name == "blockquote":
        inner = "\n\n".join(f for f in (block(c, depth) for c in node.children) if f)
        return "\n".join("> " + ln for ln in inner.split("\n"))
    if node.name in {"div", "details", "section", "article", "aside"}:
        parts = []
        if node.name == "details" and (summary := node.find("summary")):
            parts.append(f"**{inline(summary).strip()}**")
            summary.extract()
        if "admonition" in classes or "admonition" in " ".join(classes):
            if title := node.find("p", class_="admonition-title"):
                parts.append(f"> **{inline(title).strip()}**")
                title.extract()
        for child in node.children:
            if piece := block(child, depth):
                parts.append(piece)
        return "\n\n".join(parts)
    if node.name == "p":
        return inline(node).strip()
    return "\n\n".join(f for f in (block(c, depth) for c in node.children) if f)


def page_to_markdown(html: str) -> tuple[str, str]:
    soup = BeautifulSoup(html, "html.parser")
    article = soup.find("article") or soup.find("main") or soup
    for junk in article.select("nav, .md-source-file, .md-feedback, .headerlink, .md-nav"):
        junk.decompose()
    title = article.find("h1")
    title_text = inline(title).strip() if title else ""
    body = block(article)
    body = re.sub(r"\n{3,}", "\n\n", body).strip()
    return title_text, body


def write_index(out: Path, version: str, stamp: str) -> None:
    """Rebuild INDEX.md from every cached page, so a partial sync keeps the full map."""
    rows = []
    for path in sorted(out.glob("*.md")):
        if path.name == "INDEX.md":
            continue
        text = path.read_text(encoding="utf-8")
        m = re.search(r"<!-- Generated from (\S+) \(docs ([^)]+)\) on (\S+)\.", text)
        url, page_version, fetched_on = (m.group(1), m.group(2), m.group(3)) if m else ("", "?", "?")
        title = next((ln[2:].strip() for ln in text.splitlines() if ln.startswith("# ")), path.stem)
        stale = "" if page_version == version else f" _(docs {page_version})_"
        rows.append(f"| `{path.name}` | {title}{stale} | {fetched_on} | {url} |")

    (out / "INDEX.md").write_text(
        "\n".join(
            [
                "# Kotlin Toolchain documentation cache",
                "",
                f"Source: {BASE}/{version}/ — docs version **{version}**, last sync **{stamp}**.",
                "",
                "Generated by `.agents/skills/kotlin-toolchain-docs/scripts/sync_docs.py`; do not hand-edit.",
                "",
                "| Reference file | Topic | Fetched | Source |",
                "| --- | --- | --- | --- |",
                *rows,
                "",
            ]
        ),
        encoding="utf-8",
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default="latest", help="docs version segment (default: latest)")
    ap.add_argument("--out", default=None, help="output directory for reference markdown")
    ap.add_argument("--only", action="append", default=[], help="only sync URLs containing this substring")
    ap.add_argument("--list", action="store_true", help="list documentation URLs and exit")
    ap.add_argument("--delay", type=float, default=0.3, help="seconds between requests")
    args = ap.parse_args()

    urls = sitemap_urls(args.version)
    if not urls:
        print("no URLs found in sitemap", file=sys.stderr)
        return 1
    # the sitemap reports the concrete version even when fetched via /latest/
    m = re.match(rf"{re.escape(BASE)}/([^/]+)/", urls[0])
    version = m.group(1) if m else args.version

    if args.only:
        urls = [u for u in urls if any(s in u for s in args.only)]
    if args.list:
        print("\n".join(urls))
        return 0

    out = Path(args.out) if args.out else Path(__file__).resolve().parents[2] / "kotlin-toolchain" / "references"
    out.mkdir(parents=True, exist_ok=True)
    stamp = _dt.date.today().isoformat()

    fetched = 0

    for url in urls:
        slug = slug_for(url, version)
        try:
            title, body = page_to_markdown(get(url))
        except Exception as exc:  # noqa: BLE001 - report and keep going
            print(f"FAIL {url}: {exc}", file=sys.stderr)
            continue
        path = out / f"{slug}.md"
        header = (
            f"<!-- Generated from {url} (docs {version}) on {stamp}. Do not edit by hand; "
            f"run sync_docs.py to refresh. -->\n\n"
        )
        path.write_text(header + (body or f"# {title}\n"), encoding="utf-8")
        fetched += 1
        print(f"ok  {slug}.md  <- {url}")
        time.sleep(args.delay)

    write_index(out, version, stamp)
    print(f"\nwrote {fetched} page(s) + INDEX.md to {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
