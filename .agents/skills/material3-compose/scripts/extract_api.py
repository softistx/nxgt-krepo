#!/usr/bin/env python3
"""Regenerate references/ from the Compose Material 3 jar this repo actually resolves.

Material 3 in Compose Multiplatform ships on its own version line — 1.11.0-alpha07
while foundation and ui are 1.11.1 — so the androidx documentation on the web
describes a different artifact than the one that compiles here. The jar is the
only source that cannot be out of date, so the reference pages are read off it
with `javap` rather than fetched.

    python3 .agents/skills/material3-compose/scripts/extract_api.py
    python3 .agents/skills/material3-compose/scripts/extract_api.py --jar <path>
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import zipfile
from datetime import date
from pathlib import Path

SKILL_DIR = Path(__file__).resolve().parent.parent
REFERENCES = SKILL_DIR / "references"
CACHE = Path.home() / ".cache/JetBrains/Kotlin/.m2.cache/org/jetbrains/compose/material3"
PACKAGE = "androidx.compose.material3"

# Kotlin mangles the name of any function returning an inline value class, so
# `getPrimary` arrives as `getPrimary-0d7_KjU`. The suffix is an implementation
# detail of the ABI, never something a caller writes.
MANGLED = re.compile(r"-[A-Za-z0-9_]+$")


def newest_jar() -> Path:
    jars = sorted(CACHE.glob("material3-desktop/*/material3-desktop-*.jar"))
    if not jars:
        sys.exit(
            f"no material3-desktop jar under {CACHE}\n"
            "run `./kotlin build -m stx-material` once to populate the cache"
        )
    return jars[-1]


def javap(jar: Path, *classes: str) -> str:
    out = subprocess.run(
        ["javap", "-cp", str(jar), *classes],
        capture_output=True, text=True, check=False,
    )
    return out.stdout


def properties(jar: Path, cls: str) -> list[str]:
    """The public read-only properties of a class, in declaration order."""
    names = []
    for line in javap(jar, f"{PACKAGE}.{cls}").splitlines():
        m = re.search(r"\bget([A-Z][A-Za-z0-9]*)(?:-[A-Za-z0-9_]+)?\(\)", line)
        if m and "public" in line:
            name = m.group(1)[0].lower() + m.group(1)[1:]
            if name not in names:
                names.append(name)
    return names


def top_level_names(jar: Path, suffix: str) -> list[str]:
    """Class names in the package ending with `suffix` (e.g. `Defaults`, `Kt`)."""
    with zipfile.ZipFile(jar) as z:
        prefix = PACKAGE.replace(".", "/") + "/"
        return sorted(
            {
                n[len(prefix):-len(".class")]
                for n in z.namelist()
                if n.startswith(prefix)
                and n.endswith(".class")
                and "/" not in n[len(prefix):]
                and n[len(prefix):-len(".class")].endswith(suffix)
                and "$" not in n
            }
        )


def composables(jar: Path) -> list[str]:
    """Public @Composable entry points, read off the generated *Kt facade classes."""
    facades = [f"{PACKAGE}.{n}" for n in top_level_names(jar, "Kt")]
    found: set[str] = set()
    # javap over ~200 facades at once is one JVM start rather than two hundred.
    for chunk in (facades[i:i + 60] for i in range(0, len(facades), 60)):
        for line in javap(jar, *chunk).splitlines():
            m = re.search(r"\bpublic static final .*\b([A-Z][A-Za-z0-9]*)(?:-[A-Za-z0-9_]+)?\(", line)
            if m:
                found.add(m.group(1))
    return sorted(found)


def page(title: str, version: str, body: str) -> str:
    stamp = date.today().isoformat()
    return (
        f"<!-- Generated from {PACKAGE} {version} on {stamp}. "
        "Do not edit; re-run scripts/extract_api.py. -->\n\n"
        f"# {title}\n\n{body}\n"
    )


def bullets(names: list[str]) -> str:
    return "\n".join(f"- `{n}`" for n in names)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", help="material3-desktop jar to read (default: newest in the cache)")
    args = ap.parse_args()

    jar = Path(args.jar) if args.jar else newest_jar()
    version = jar.stem.replace("material3-desktop-", "")
    REFERENCES.mkdir(parents=True, exist_ok=True)

    written: list[tuple[str, str]] = []
    for cls, filename, title in (
        ("ColorScheme", "color-scheme.md", "ColorScheme roles"),
        ("Typography", "typography.md", "Typography styles"),
        ("Shapes", "shapes.md", "Shape slots"),
    ):
        names = properties(jar, cls)
        body = (
            f"The {len(names)} public members of `{PACKAGE}.{cls}`, read off the jar.\n\n"
            + bullets(names)
        )
        (REFERENCES / filename).write_text(page(title, version, body), encoding="utf-8")
        written.append((filename, f"{len(names)} members"))

    defaults = top_level_names(jar, "Defaults")
    (REFERENCES / "defaults.md").write_text(
        page(
            "Defaults objects",
            version,
            "Every `*Defaults` object in the package. These hold the colours, shapes, "
            "elevations and sizes a component falls back to, and are the supported way "
            "to restyle one without reimplementing it.\n\n" + bullets(defaults),
        ),
        encoding="utf-8",
    )
    written.append(("defaults.md", f"{len(defaults)} objects"))

    comps = composables(jar)
    (REFERENCES / "components.md").write_text(
        page(
            "Public entry points",
            version,
            "Every public top-level function in the package — the components plus the "
            "`remember*` state factories. Presence here is what settles whether a "
            "component exists in this version at all.\n\n" + bullets(comps),
        ),
        encoding="utf-8",
    )
    written.append(("components.md", f"{len(comps)} functions"))

    index = [
        f"<!-- Generated on {date.today().isoformat()}; do not edit. -->",
        "",
        f"# Material 3 API reference ({version})",
        "",
        "| File | Holds |",
        "| --- | --- |",
    ]
    index += [f"| `{f}` | {what} |" for f, what in written]
    (REFERENCES / "INDEX.md").write_text("\n".join(index) + "\n", encoding="utf-8")

    (SKILL_DIR / "api-source.json").write_text(
        json.dumps(
            {"artifact": f"{PACKAGE} (org.jetbrains.compose.material3)",
             "version": version, "last_sync": date.today().isoformat()},
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    for f, what in written:
        print(f"ok  {f}  ({what})")
    print(f"\nwrote {len(written)} page(s) + INDEX.md from {jar.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
