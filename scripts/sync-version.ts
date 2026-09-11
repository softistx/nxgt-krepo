// Propagates the version Changesets just wrote into package.json out to the two files
// that actually carry it for the Kotlin build.
//
// The toolchain has no way to override `settings.publishing.version` — no -P, no environment
// variable, and no `${...}` interpolation in a module.yaml — so the version is a literal line in
// the shared template. And `libs.versions.toml` carries it a second time, because the examples and
// the servers resolve `io.github.softistx:stx-*` as published artifacts rather than as modules.
//
// The two must move together: a bump that touches only one leaves every example resolving a
// coordinate that was never published. That is the whole reason this file exists, and why the
// count assertion below is not defensive padding — it is the check that fails loudly when someone
// reshapes a manifest and silently breaks the release.

import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

/** One place a version literal lives, and how to rewrite it. */
export interface Anchor {
    /** Path relative to the repository root. */
    readonly file: string;
    /** Must match exactly once in `file`; the capture groups feed `replacement`. */
    readonly pattern: RegExp;
    /** A `String.replace` template, built for the version being written. */
    readonly replacement: (version: string) => string;
}

/** What one anchor did. */
export interface Written {
    readonly file: string;
    readonly changed: boolean;
}

/** The two anchors, each of which must match exactly once. */
export const ANCHORS: readonly Anchor[] = [
    {
        file: "publishing.module-template.yaml",
        // The only `version:` line in the file; it is what all 45 libraries publish under.
        pattern: /^([ \t]*version:[ \t]*)\S+$/m,
        replacement: (version) => `$1${version}`,
    },
    {
        file: "libs.versions.toml",
        // `stx = "0.1.0"` under [versions] — the ref behind all 19 `stx-*` catalog aliases.
        pattern: /^(stx[ \t]*=[ \t]*")[^"]+(")$/m,
        replacement: (version) => `$1${version}$2`,
    },
];

export function syncVersion(version: string, root: string = ROOT): Written[] {
    const written: Written[] = [];
    for (const { file, pattern, replacement } of ANCHORS) {
        const path = root + file;
        const before = readFileSync(path, "utf8");
        const hits = before.match(new RegExp(pattern.source, pattern.flags + "g")) ?? [];
        if (hits.length !== 1) {
            throw new Error(
                `${file}: expected exactly one match for ${pattern}, found ${hits.length}. ` +
                    `The file was reshaped and this script no longer knows where the version lives.`,
            );
        }
        const after = before.replace(pattern, replacement(version));
        if (after !== before) writeFileSync(path, after);
        written.push({ file, changed: after !== before });
    }
    return written;
}

if (import.meta.main) {
    const { version } = JSON.parse(readFileSync(ROOT + "package.json", "utf8")) as { version: string };
    for (const { file, changed } of syncVersion(version)) {
        console.log(`${changed ? "updated" : "unchanged"}  ${file}  -> ${version}`);
    }
}
