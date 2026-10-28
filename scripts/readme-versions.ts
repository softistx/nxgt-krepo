// Keeps the `io.github.softistx:<artifact>:<version>` coordinates written in README.md files at the
// version of the family that owns <artifact>.
//
// A README is prose, so nobody remembers to bump a snippet in it when a Version PR lands — the root
// README sat two minors behind `stx-jpa` and `stx-material`'s three. `sync-version.ts` calls this
// beside the template and catalog anchors, so the same PR moves all of them, and `--check` fails on
// the drift like it does for those.
//
// Only README.md files are touched: the root one and those under `libs/`. `docs/` is deliberately
// out — `docs/consuming.md` keeps old versions on purpose, to show what a pinned consumer writes.
// An artifact no family publishes is left alone.

import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { families } from "./families.ts";

/** A coordinate with a version: group 1 is the `group:artifact:` part, group 2 the artifact. */
const COORDINATE = /(io\.github\.softistx:([\w-]+):)\d+\.\d+\.\d+(?:-[\w.]+)?/g;

/** What rewriting one README did, or would do. */
export interface ReadmeWritten {
    readonly file: string;
    readonly changed: boolean;
}

/** Every README.md the script owns: the root one, and any under `libs/`, sorted. */
export function readmes(root: string): string[] {
    const found = existsSync(root + "README.md") ? ["README.md"] : [];
    const walk = (dir: string) => {
        for (const entry of readdirSync(root + dir, { withFileTypes: true })) {
            if (entry.name === "node_modules" || entry.name === "build" || entry.name.startsWith(".")) continue;
            const path = `${dir}/${entry.name}`;
            if (entry.isDirectory()) walk(path);
            else if (entry.name === "README.md") found.push(path);
        }
    };
    walk("libs");
    return found.sort();
}

/** @returns each README that carries a versioned coordinate, and whether it needed rewriting. */
export function syncReadmeVersions(root: string, check = false): ReadmeWritten[] {
    const versionOf = new Map<string, string>();
    for (const family of families(root)) {
        for (const module of family.modules) versionOf.set(module.name, family.version);
    }

    const written: ReadmeWritten[] = [];
    for (const file of readmes(root)) {
        const before = readFileSync(root + file, "utf8");
        if (!before.match(COORDINATE)) continue;
        const after = before.replace(COORDINATE, (whole, prefix: string, artifact: string) => {
            const version = versionOf.get(artifact);
            return version ? prefix + version : whole;
        });
        if (after !== before && !check) writeFileSync(root + file, after);
        written.push({ file, changed: after !== before });
    }
    return written;
}
