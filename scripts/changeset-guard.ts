// A pull request that changes a library declares what it does to that library's published version.
//
// This is the only step of the release circuit nobody can automate: whether a change is a patch, a
// minor or a major is a judgement about what a consumer will experience, and no diff contains it.
// So CI asks for it, and this is the asking.
//
// It used to be "the PR touches libs/ and adds no .changeset/*.md". That was enough while there was
// one version for everything. Now a changeset names *which families* it releases, and a changeset
// naming the wrong one is worse than none: it bumps a line nobody changed and leaves the changed
// one behind, silently.

import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { type Family, families } from "./families.ts";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

export type Bump = "major" | "minor" | "patch";

/** A `.changeset/*.md` as it matters here: which families it releases, and how far. */
export interface Changeset {
    readonly file: string;
    readonly bumps: ReadonlyMap<string, Bump>;
    /**
     * Dependents the author states a `major` cannot reach, from an `unaffected:` line in the prose.
     *
     * The escape hatch for the rule below, and deliberately a list of names rather than a blanket
     * opt-out: the author has to look at each dependent and say so. A name that is not a dependent
     * of a major in this changeset is a typo, and reported as one.
     */
    readonly unaffected: ReadonlySet<string>;
}

/** `true` when a changeset declares that nothing published changes. */
export const isEmpty = (c: Changeset) => c.bumps.size === 0;

const BUMPS: readonly string[] = ["major", "minor", "patch"];

/**
 * Reads the front matter of a changeset.
 *
 * Deliberately not a YAML parser: the front matter Changesets writes is a flat list of
 * `"name": bump`, and anything else in there is a mistake worth reporting rather than accommodating.
 */
export function parse(file: string, text: string): Changeset {
    const match = text.match(/^---\r?\n([\s\S]*?)\r?\n?---/);
    if (!match) throw new Error(`${file}: no --- front matter. Write it with \`bun changeset\`.`);

    // `unaffected: stx-jpa, stx-mongo` anywhere in the prose. One line, so a changeset that needs
    // it says so once rather than annotating each name.
    const unaffected = new Set<string>();
    for (const line of text.slice(match[0].length).split("\n")) {
        const hatch = line.match(/^\s*unaffected:\s*(.+?)\s*$/i);
        if (!hatch) continue;
        for (const name of (hatch[1] ?? "").split(",")) {
            const trimmed = name.trim();
            if (trimmed) unaffected.add(trimmed);
        }
    }

    const bumps = new Map<string, Bump>();
    for (const line of (match[1] ?? "").split("\n")) {
        if (!line.trim()) continue;
        const entry = line.match(/^\s*["']?([^"':]+)["']?\s*:\s*["']?(\w+)["']?\s*$/);
        if (!entry) throw new Error(`${file}: cannot read '${line.trim()}' as \`"name": bump\`.`);
        const [, name = "", bump = ""] = entry;
        if (!BUMPS.includes(bump)) {
            throw new Error(`${file}: '${name}' is '${bump}'; expected major, minor or patch.`);
        }
        bumps.set(name, bump as Bump);
    }
    return { file, bumps, unaffected };
}

/** Every changeset currently in `.changeset/`, README excluded. */
export function changesets(root: string = ROOT): Changeset[] {
    return readdirSync(root + ".changeset")
        .filter((f) => f.endsWith(".md") && f !== "README.md")
        .sort()
        .map((f) => parse(`.changeset/${f}`, readFileSync(`${root}.changeset/${f}`, "utf8")));
}

/** The family a changed path belongs to, or `undefined` if it is not under a family. */
export function familyOf(path: string, all: readonly Family[]): Family | undefined {
    return all.find((f) => path.startsWith(`${f.dir}/`));
}

export interface Verdict {
    /** Why the pull request fails. Empty means it passes. */
    readonly errors: readonly string[];
}

/**
 * Judges a pull request from the paths it changed and the changesets it carries.
 *
 * @param changed paths relative to the repository root, as `git diff --name-only` prints them.
 */
export function judge(
    changed: readonly string[],
    present: readonly Changeset[],
    all: readonly Family[],
): Verdict {
    const errors: string[] = [];

    // A name that is not a family releases nothing. `ignore` in .changeset/config.json already stops
    // the repository itself from being versioned, but it does so silently — this is the loud half.
    const names = new Set(all.map((f) => f.name));
    for (const c of present) {
        for (const name of c.bumps.keys()) {
            if (!names.has(name)) {
                errors.push(
                    `${c.file} names '${name}', which is not a library family. A changeset names a ` +
                        `family — one of ${all.length} directories under libs/ — never an artifact ` +
                        `and never this repository. \`bun changeset\` lists them.`,
                );
            }
        }
    }

    const declared = new Set(present.flatMap((c) => [...c.bumps.keys()]));

    // Before anything about changed paths: what a `major` obliges is a property of the changesets
    // alone. A pull request can declare one while touching no library at all — reverting a
    // changeset's prose, say — and the obligation is the same either way.
    errors.push(...majors(present, declared, all));

    const touched = new Map<string, Family>();
    for (const path of changed) {
        const family = familyOf(path, all);
        if (family) touched.set(family.name, family);
    }
    if (touched.size === 0) return { errors };

    // An empty changeset is the explicit "nothing published changes", and it covers everything.
    if (present.some(isEmpty)) return { errors };

    if (present.length === 0) {
        errors.push(
            `this pull request changes ${[...touched.keys()].sort().join(", ")} but adds no ` +
                `changeset. Run \`bun changeset\`, or \`bun changeset --empty\` if nothing ` +
                `published changes.`,
        );
        return { errors };
    }

    const missing = [...touched.keys()].filter((n) => !declared.has(n)).sort();
    if (missing.length) {
        errors.push(
            `changed but not declared in any changeset: ${missing.join(", ")}. Add them, or add an ` +
                `empty changeset if their published artifacts do not change.`,
        );
    }

    return { errors };
}

/**
 * A `major` has to say what happens to the families that depend on it.
 *
 * Changesets only ever gives a dependent a `patch`, whatever the dependency did. Since a POM pins
 * its dependencies at an exact version, a consumer who takes that patch gets the major
 * transitively — so the author names each dependent with the bump it deserves, or states on an
 * `unaffected:` line that the break cannot reach that dependent's own consumers.
 *
 * This is an error and not a warning because the cost is paid by someone who is not in the room.
 * A warning on a rare event is a warning people learn to scroll past; the hatch is what keeps it
 * from being a wall.
 */
function majors(
    present: readonly Changeset[],
    declared: ReadonlySet<string>,
    all: readonly Family[],
): string[] {
    const problems: string[] = [];
    const excused = new Set(present.flatMap((c) => [...c.unaffected]));
    const everyDependent = new Set<string>();

    for (const c of present) {
        for (const [name, bump] of c.bumps) {
            if (bump !== "major") continue;
            const dependents = all.filter((f) => f.deps.includes(name)).map((f) => f.name);
            for (const d of dependents) everyDependent.add(d);

            const unnamed = dependents.filter((d) => !declared.has(d) && !excused.has(d)).sort();
            if (!unnamed.length) continue;

            problems.push(
                `${c.file} is a major for '${name}', and ${unnamed.join(", ")} depend${
                    unnamed.length === 1 ? "s" : ""
                } on it at runtime. Changesets would give ${
                    unnamed.length === 1 ? "it" : "them"
                } a patch — it never gives a dependent more — while the POM points at the new ` +
                    `major, so a consumer takes the break on a patch.\n` +
                    `Add to ${c.file}'s front matter:\n` +
                    unnamed.map((d) => `  "${d}": major`).join("\n") +
                    `\nOr, if the break genuinely cannot reach a consumer of ${
                        unnamed.length === 1 ? "that family" : "those families"
                    }, say so in the prose:\n  unaffected: ${unnamed.join(", ")}`,
            );
        }
    }

    // A name on an `unaffected:` line that depends on no major in this changeset excuses nothing,
    // and is almost always a typo in the name it was meant to excuse.
    for (const c of present) {
        for (const name of c.unaffected) {
            if (everyDependent.has(name)) continue;
            problems.push(
                `${c.file} says '${name}' is unaffected, but it does not depend on any family this ` +
                    `changeset bumps to major. Check the spelling: the line excuses a *dependent*, ` +
                    `not the family being broken.`,
            );
        }
    }

    return problems;
}

if (import.meta.main) {
    const base = process.argv[process.argv.indexOf("--base") + 1];
    if (!base || base === "--base") {
        console.error("usage: changeset-guard.ts --base <ref> [--head <ref>]");
        process.exit(2);
    }
    const headIndex = process.argv.indexOf("--head");
    const head = headIndex > 0 ? process.argv[headIndex + 1] : "HEAD";

    const diff = Bun.spawnSync(["git", "diff", "--name-only", `${base}...${head}`]);
    if (diff.exitCode !== 0) {
        console.error(diff.stderr.toString().trim());
        process.exit(1);
    }
    const changed = diff.stdout.toString().split("\n").filter(Boolean);

    const { errors } = judge(changed, changesets(), families());
    // A multi-line annotation needs its newlines escaped, or Actions keeps only the first line —
    // which for the major rule would drop the lines the author is meant to paste.
    for (const e of errors) console.log(`::error::${e.replaceAll("\n", "%0A")}`);
    if (errors.length) process.exit(1);
    console.log("Every changed family is declared.");
}
