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
import os
import re
import sys
import time
import urllib.request
from pathlib import Path
from urllib.parse import urljoin, urlparse
from xml.etree import ElementTree

from bs4 import BeautifulSoup, NavigableString, Tag

SKILLS_DIR = Path(__file__).resolve().parents[2]
CONFIG_NAME = "docs-source.json"
UA = {"User-Agent": "nxgt-krepo-docs-fetch/1.0"}

# Site chrome that must never reach the cache. MkDocs Material ships the first
# three; the rest are per-site widgets seen in the wild.
SKIP_CLASSES = {
    "md-source-file",
    "md-feedback",
    "headerlink",
    "git-revision-date-localized-plugin",
}
CHROME_SELECTORS = (
    "nav, .md-source-file, .md-feedback, .headerlink, .md-nav, "
    ".git-revision-date-localized-plugin"
)
# "Documentation issue? Report or edit" banners, floated at the top of an article.
CHROME_TEXT = re.compile(r"documentation issue|report an issue|edit this page", re.I)
SIZE_HINT_BYTES = 8192


def get(url: str) -> str:
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8", "replace")


def load_config(skill: str) -> tuple[Path, dict]:
    skill_dir = SKILLS_DIR / skill
    config_path = skill_dir / CONFIG_NAME
    if not config_path.is_file():
        raise SystemExit(f"no {CONFIG_NAME} in {skill_dir} — create one before fetching")
    return skill_dir, json.loads(config_path.read_text(encoding="utf-8"))


# The scheme and host of the docs site, set by `main` from the configured base.
SITE_ORIGIN = ""


def common_prefix(urls: list[str]) -> str:
    """The root the sitemap declares for itself, which may not be where it is served."""
    if len(urls) == 1:
        parsed = urlparse(urls[0])
        return f"{parsed.scheme}://{parsed.netloc}/"
    prefix = os.path.commonprefix(urls)
    return prefix[: prefix.rindex("/") + 1] if "/" in prefix else prefix


def discover(
    base: str, sitemap: str | None = None, method: str = "auto"
) -> tuple[list[str], str]:
    """Return (page URLs, the root they are relative to).

    Tries llms.txt, then sitemap.xml, then the nav links on the base page. A
    sitemap whose <loc> host differs from `base` has a misconfigured site_url
    (Ktorfit's points at its GitHub repo), so its paths are re-joined onto base.

    `method` pins one of them instead. Some sites serve a *full-text* llms.txt —
    the whole documentation inlined rather than a link index — and harvesting
    URLs out of it yields only the handful that prose happens to link to, each
    carrying the trailing `)` or `.` of the sentence it sat in. kotlinlang.org
    is one, which is why its config says `"discovery": "sitemap"`.
    """
    if method not in ("auto", "llms", "sitemap", "nav"):
        raise SystemExit(f"unknown discovery method {method!r}")

    if method in ("auto", "llms"):
        try:
            llms = get(urljoin(base, "llms.txt"))
            urls = sorted({m for m in re.findall(r"https?://\S+", llms) if m.startswith(base)})
            if urls:
                return urls, base
        except Exception:  # noqa: BLE001 - absence is the common case
            pass
        if method == "llms":
            raise SystemExit(f"no pages found in {urljoin(base, 'llms.txt')}")

    try:
        if method == "nav":
            raise RuntimeError("skipped: discovery pinned to nav")
        xml = get(sitemap or urljoin(base, "sitemap.xml"))
        ns = {"s": "http://www.sitemaps.org/schemas/sitemap/0.9"}
        locs = sorted(
            loc.text.strip()
            for loc in ElementTree.fromstring(xml).findall(".//s:loc", ns)
            if loc.text
        )
        if locs:
            root = common_prefix(locs)
            if urlparse(root).netloc != urlparse(base).netloc:
                print(
                    f"note: sitemap declares {root} but docs are served from {base}; "
                    "rebasing paths onto the configured base",
                    file=sys.stderr,
                )
                return [urljoin(base, loc[len(root):]) for loc in locs], base
            return locs, root
    except Exception as exc:  # noqa: BLE001
        print(f"note: sitemap discovery failed ({exc}); falling back to nav links", file=sys.stderr)

    soup = BeautifulSoup(get(base), "html.parser")
    hrefs = {a.get("href", "") for a in soup.select(".md-nav a, nav a, a")}
    urls = sorted(
        {urljoin(base, h) for h in hrefs if h and not h.startswith(("#", "mailto:"))}
    )
    return [u for u in urls if u.startswith(base)], base


def slug_for(url: str, root: str) -> str:
    path = url[len(root):] if url.startswith(root) else urlparse(url).path
    return (path.strip("/").replace("/", "-") or "home").lower()


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
    if node.name == "pre" or is_code_div(node):
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
            # Root-relative link: absolutise it against the site's origin, which
            # `main` records once per run. kotlinlang.org writes its cross-page
            # links this way; MkDocs sites write them relative, which is why this
            # branch stayed dead — and undefined — until now.
            href = SITE_ORIGIN + href
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


# Writerside (kotlinlang.org) renders code as a plain <div class="code-block">
# or <div class="code-collapse"> holding newline-preserved text, with the
# language in data-lang and no <pre> anywhere on the page. Without this, every
# sample collapses onto one line as ordinary prose.
CODE_DIV_CLASSES = {"code-block", "code-collapse"}


def is_code_div(node: Tag) -> bool:
    return node.name == "div" and bool(set(node.get("class", [])) & CODE_DIV_CLASSES)


def code_block(pre: Tag) -> str:
    code = pre.find("code") or pre
    lang = pre.get("data-lang", "") if isinstance(pre, Tag) else ""
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
    if node.name == "pre" or is_code_div(node):
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
    for junk in article.select(CHROME_SELECTORS):
        junk.decompose()
    # Floated "Documentation issue? Report / edit" banners carry no class of
    # their own, so they are matched on their text instead.
    for span in article.find_all(["span", "div", "p"], recursive=True):
        if "float" in (span.get("style") or "") and CHROME_TEXT.search(span.get_text()):
            span.decompose()
    title = article.find("h1")
    title_text = inline(title).strip() if title else ""
    body = block(article)
    body = re.sub(r"^Last update:.*$", "", body, flags=re.M)
    body = re.sub(r"\n{3,}", "\n\n", body).strip()
    return title_text, body


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

    global SITE_ORIGIN
    SITE_ORIGIN = "{0.scheme}://{0.netloc}".format(urlparse(base))

    urls, root = discover(base, cfg.get("sitemap"), cfg.get("discovery", "auto"))
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
