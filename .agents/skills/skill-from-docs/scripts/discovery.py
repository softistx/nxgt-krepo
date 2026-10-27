"""Find the pages of a documentation site: llms.txt, sitemap.xml, nav links or a listed set."""
from __future__ import annotations

import os
import re
import sys
import urllib.request
from urllib.parse import urljoin, urlparse
from xml.etree import ElementTree

from bs4 import BeautifulSoup

UA = {"User-Agent": "nxgt-krepo-docs-fetch/1.0"}


def get(url: str) -> str:
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8", "replace")



def common_prefix(urls: list[str]) -> str:
    """The root the sitemap declares for itself, which may not be where it is served."""
    if len(urls) == 1:
        parsed = urlparse(urls[0])
        return f"{parsed.scheme}://{parsed.netloc}/"
    prefix = os.path.commonprefix(urls)
    return prefix[: prefix.rindex("/") + 1] if "/" in prefix else prefix


def discover(
    base: str,
    sitemap: str | None = None,
    method: str = "auto",
    pages: list[str] | None = None,
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

    `"discovery": "list"` skips discovery: the config's `pages` array is the page list, each entry
    a URL or a path relative to `base`. Use it when the site has no usable index
    (developer.android.com) or a skill draws a few pages from several sites. The config's
    `exclude` regexes are matched against a page's path relative to `base`, or against the full URL
    for a page outside it.
    """
    if method not in ("auto", "llms", "sitemap", "nav", "list"):
        raise SystemExit(f"unknown discovery method {method!r}")

    if method == "list":
        # An explicit list, for a site with no usable sitemap or nav (developer.android.com
        # redirects its section roots) or a skill that draws a few pages from several sites.
        # Each entry is a URL, or a path relative to `base`.
        if not pages:
            raise SystemExit('"discovery": "list" needs a "pages" array in the config')
        return [urljoin(base, p) for p in pages], base

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
