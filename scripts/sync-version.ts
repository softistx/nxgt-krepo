// Propagates the versions Changesets just wrote into the families' `package.json` out to the two
// places the Kotlin build actually reads them.
//
// The toolchain cannot be told a version from the command line — no `-P`, no environment variable,
// no `${...}` in a `module.yaml` — so a version is a literal line in a manifest. Per family that is
// `libs/<role>/<family>/<family>.module-template.yaml`, which every module of that family applies.
// And `libs.versions.toml` carries each one a second time, because the examples and the servers
// resolve *published coordinates* rather than module paths.
//
// The two must move together: a bump that touches only one leaves an example resolving a coordinate
// that was never published. That is the whole reason this file exists, and why every assertion
// below is not defensive padding — each is the check that fails loudly when someone reshapes a file
// and would otherwise silently break the release.
//
// `--check` writes nothing and exits non-zero on any disagreement. CI runs it on every pull
// request, and the release job runs it before touching anything, because a second is a cheaper
// failure than a half-finished upload to a repository that does not allow deletions.

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { type Family, families, templateOf } from "./families.ts";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

export const CATALOG = "libs.versions.toml";
export const SHARED_TEMPLATE = "publishing.module-template.yaml";

/** One place a version literal lives, and how to rewrite it. */
export interface Anchor {
    /** Path relative to the repository root. */
    readonly file: string;
    /** What the file should say, once rewritten. */
    readonly version: string;
    /** Must match exactly once in the region searched; group 1 is kept, group 2 replaced. */
    readonly pattern: RegExp;
    /**
     * The slice of the file to search, for an anchor whose pattern is only unique within one.
     * Everything outside it is left alone.
     */
    readonly region?: (text: string) => readonly [number, number];
}

/** What one anchor did, or would do. */
export interface Written {
    readonly file: string;
    readonly version: string;
    readonly changed: boolean;
}

/**
 * The `[versions]` table of the catalog.
 *
 * Scoped rather than searched whole, because a family's name is also a `[libraries]` alias — and
 * `stx-jpa = { module = … }` down there would otherwise be a second match for an anchor looking for
 * `stx-jpa = "…"`. It is not one today, but the day someone writes a version inline it would be.
 */
function versionsTable(text: string): readonly [number, number] {
    const start = text.indexOf("[versions]");
    if (start < 0) throw new Error(`${CATALOG}: no [versions] table. The catalog was reshaped.`);
    const next = text.indexOf("\n[", start + 1);
    return [start, next < 0 ? text.length : next];
}

/** The two anchors a family has: its own template, and its key in the catalog. */
export function anchorsFor(family: Family): Anchor[] {
    return [
        {
            file: templateOf(family),
            version: family.version,
            // The only `version:` line in that file — it holds nothing else. Deliberately not in
            // the `module.yaml` files: twelve of those carry `settings.ktor.version` and one
            // carries `settings.compose.version`, so an anchor there could never be this simple.
            pattern: /^([ \t]*version:[ \t]*)(\S+)[ \t]*$/m,
        },
        {
            file: CATALOG,
            version: family.version,
            // `stx-jpa = "0.2.1"` under [versions] — the ref behind that family's catalog aliases.
            pattern: new RegExp(`^(${family.name}[ \\t]*=[ \\t]*")([^"]+)(?=")`, "m"),
            region: versionsTable,
        },
    ];
}

/** Applies one anchor to `text`, asserting it matches exactly once. @returns the new text. */
function apply(text: string, { file, version, pattern, region }: Anchor): string {
    const [from, to] = region ? region(text) : ([0, text.length] as const);
    const slice = text.slice(from, to);

    const hits = slice.match(new RegExp(pattern.source, pattern.flags.replace("g", "") + "g")) ?? [];
    if (hits.length !== 1) {
        throw new Error(
            `${file}: expected exactly one match for ${pattern}${region ? " in [versions]" : ""}, ` +
                `found ${hits.length}. The file was reshaped and this script no longer knows where ` +
                `the version lives.`,
        );
    }
    return text.slice(0, from) + slice.replace(pattern, `$1${version}`) + text.slice(to);
}

/**
 * Writes every family's version into its template and into the catalog.
 *
 * With `check`, nothing is written and the result still reports what would have changed — which is
 * exactly the drift a caller wants to fail on.
 */
export function syncVersions(root: string = ROOT, check = false): Written[] {
    const written: Written[] = [];
    // Grouped by file so the catalog is read once and written once, rather than 17 times.
    const edits = new Map<string, Anchor[]>();
    for (const family of families(root)) {
        for (const anchor of anchorsFor(family)) {
            const group = edits.get(anchor.file);
            if (group) group.push(anchor);
            else edits.set(anchor.file, [anchor]);
        }
    }

    for (const [file, anchors] of edits) {
        const before = readFileSync(root + file, "utf8");
        let after = before;
        for (const anchor of anchors) after = apply(after, anchor);
        if (after !== before && !check) writeFileSync(root + file, after);
        for (const anchor of anchors) {
            // Per-anchor, so that a report names the family whose version moved, not just the file.
            const one = apply(before, anchor);
            written.push({ file: anchor.file, version: anchor.version, changed: one !== before });
        }
    }
    return written;
}

/** A `## 1.2.3` heading's version, or null for a heading that is not a release. */
function headingVersion(line: string): readonly number[] | null {
    const m = line.trimEnd().match(/^##[ \t]+(\d+)\.(\d+)\.(\d+)$/);
    return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null;
}

/** `a > b`, on the numeric triples `headingVersion` produces. */
function ahead(a: readonly number[], b: readonly number[]): boolean {
    const i = a.findIndex((n, j) => n !== b[j]);
    return i >= 0 && a[i]! > b[i]!;
}

/**
 * The structural assertions that the anchors above take for granted.
 *
 * `families()` already refuses a family with no `package.json` or a mismatched name. What is left
 * is the wiring between a family and its modules, and it is worth checking because its failure mode
 * is the expensive one: a module wired to no family template publishes with no version at all, and
 * `./kotlin build` does not notice — only `./kotlin publish` does, in the release job, after the
 * version has been bumped and a release is expected.
 *
 * @returns one message per problem; empty when the repository is wired correctly.
 */
export function structure(root: string = ROOT): string[] {
    const problems: string[] = [];
    for (const family of families(root)) {
        const template = templateOf(family);
        const body = readFileSync(root + template, "utf8");
        if (!body.includes(`//${SHARED_TEMPLATE}`)) {
            problems.push(
                `${template} does not apply //${SHARED_TEMPLATE}, so ${family.name} would publish ` +
                    `with no group, no POM metadata and no sources jar.`,
            );
        }
        // A CHANGELOG section for a version the family has not reached. Changesets writes the
        // section and the `package.json` version in the same breath, so the only way to have one
        // without the other is a hand edit or a `changeset version` run that was reverted by
        // halves — which is exactly how fourteen of these were left behind by a dry run. Nothing
        // notices until the family genuinely reaches that version: `changeset version` then
        // prepends a *second* section with the same heading, and the release note and the
        // annotated tag take whichever one comes first.
        const changelog = `${family.dir}/CHANGELOG.md`;
        const at = headingVersion(`## ${family.version}`);
        if (!existsSync(root + changelog)) {
            problems.push(
                `${family.dir} has no CHANGELOG.md. It is where a release note comes from, so the ` +
                    `family would be released with an empty one.`,
            );
            continue;
        }
        for (const line of readFileSync(root + changelog, "utf8").split("\n")) {
            const v = headingVersion(line);
            if (v && at && ahead(v, at)) {
                problems.push(
                    `${changelog} has a '${line.trim()}' section, but ${family.name} is at ` +
                        `${family.version}. A section for a version that was never released will ` +
                        `be duplicated the day it is, and the release notes take the first of the ` +
                        `two. Remove it, or move the family's package.json to that version.`,
                );
            }
        }

        for (const module of family.modules) {
            const manifest = readFileSync(`${root}${module.dir}/module.yaml`, "utf8");
            const applied = [...manifest.matchAll(/^[ \t]*-[ \t]*\/\/(\S*\.module-template\.yaml)/gm)]
                .map(([, path]) => path ?? "");
            if (!applied.includes(template)) {
                problems.push(
                    `${module.dir}/module.yaml does not apply //${template}. It would publish with ` +
                        `no version — "Missing 'version' in publishing settings" — and only at ` +
                        `publish time, never at build time.`,
                );
            }
            if (applied.includes(SHARED_TEMPLATE)) {
                problems.push(
                    `${module.dir}/module.yaml applies //${SHARED_TEMPLATE} directly, alongside ` +
                        `//${template}. Two templates with no apply between them that both set a ` +
                        `version are a conflict; go through the family template only.`,
                );
            }
        }
    }
    return problems;
}

if (import.meta.main) {
    const check = process.argv.includes("--check");

    const problems = structure();
    for (const problem of problems) console.error(`  ${problem}`);
    if (problems.length) {
        console.error(`\n${problems.length} structural problem(s) under libs/.`);
        process.exit(1);
    }

    const written = syncVersions(ROOT, check);
    const drifted = written.filter((w) => w.changed);
    for (const { file, version, changed } of written) {
        if (changed || !check) console.log(`${changed ? "updated " : "unchanged"}  ${file}  -> ${version}`);
    }
    if (check && drifted.length) {
        console.error(
            `\n${drifted.length} anchor(s) disagree with the families' package.json. ` +
                `Run \`bun scripts/sync-version.ts\` and commit the result.`,
        );
        process.exit(1);
    }
    if (check) console.log(`${written.length} anchors agree with the families' package.json.`);
}
