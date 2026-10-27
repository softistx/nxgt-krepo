"""Convert a documentation page's HTML to markdown."""
from __future__ import annotations

import re

from bs4 import BeautifulSoup, NavigableString, Tag


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


# The scheme and host of the docs site, set by `main` from the configured base.
SITE_ORIGIN = ""


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
    # developer.android.com puts a breadcrumb trail ("Android Developers Develop ...") above the h1.
    for crumbs in article.select("ul.devsite-breadcrumb-list"):
        crumbs.decompose()
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
    # developer.android.com appends its "save to a collection" prompt to every h1.
    body = re.sub(r"\s+Stay organized with collections\s+Save and categorize content based on your preferences\.", "", body)
    title_text = re.sub(r"\s+Stay organized with collections.*$", "", title_text)
    body = re.sub(r"\n{3,}", "\n\n", body).strip()
    return title_text, body
