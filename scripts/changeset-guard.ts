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
    return { file, bumps };
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
    /** Worth saying, but not worth blocking on. */
    readonly warnings: readonly string[];
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
    const warnings: string[] = [];

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

    const touched = new Map<string, Family>();
    for (const path of changed) {
        const family = familyOf(path, all);
        if (family) touched.set(family.name, family);
    }
    if (touched.size === 0) return { errors, warnings };

    // An empty changeset is the explicit "nothing published changes", and it covers everything.
    if (present.some(isEmpty)) return { errors, warnings };

    if (present.length === 0) {
        errors.push(
            `this pull request changes ${[...touched.keys()].sort().join(", ")} but adds no ` +
                `changeset. Run \`bun changeset\`, or \`bun changeset --empty\` if nothing ` +
                `published changes.`,
        );
        return { errors, warnings };
    }

    const declared = new Set(present.flatMap((c) => [...c.bumps.keys()]));
    const missing = [...touched.keys()].filter((n) => !declared.has(n)).sort();
    if (missing.length) {
        errors.push(
            `changed but not declared in any changeset: ${missing.join(", ")}. Add them, or add an ` +
                `empty changeset if their published artifacts do not change.`,
        );
    }

    // Changesets only ever gives a dependent a `patch`, whatever the dependency did. Since a POM
    // pins its dependencies at an exact version, a consumer taking that patch gets the major
    // transitively — so a major has to name its dependents itself.
    for (const c of present) {
        for (const [name, bump] of c.bumps) {
            if (bump !== "major") continue;
            const dependents = all.filter((f) => f.deps.includes(name)).map((f) => f.name);
            const unnamed = dependents.filter((d) => !declared.has(d)).sort();
            if (unnamed.length) {
                warnings.push(
                    `${c.file} is a major for '${name}', but ${unnamed.join(", ")} depend${
                        unnamed.length === 1 ? "s" : ""
                    } on it and ${unnamed.length === 1 ? "is" : "are"} not named. They will be ` +
                        `bumped a patch — Changesets never gives a dependent more — and their POMs ` +
                        `will point at the new major, so a consumer takes the break on a patch. ` +
                        `Name them with the bump they deserve.`,
                );
            }
        }
    }

    return { errors, warnings };
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

    const { errors, warnings } = judge(changed, changesets(), families());
    for (const w of warnings) console.log(`::warning::${w}`);
    for (const e of errors) console.log(`::error::${e}`);
    if (errors.length) process.exit(1);
    console.log("Every changed family is declared.");
}
