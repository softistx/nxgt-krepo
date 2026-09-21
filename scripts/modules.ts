// The list of publishable modules, and the two shapes the build commands need it in.
//
// This used to be a shell one-liner, repeated in four places:
//
//     grep -rl publishing.module-template.yaml libs --include=module.yaml \
//       | xargs -n1 dirname | xargs -n1 basename
//
// It read "every module that applies the publishing template", which was the same thing as "every
// library" only for as long as every `module.yaml` named that template directly. Once a module
// applies its family's template instead — which is what gives each family its own version — the
// grep matches nothing and returns an *empty* list. Nothing fails: `kotlin publish` with no `-m`
// publishes nothing and exits 0, and the examples then build against whatever stale artifacts the
// runner's ~/.m2 happens to hold. That is the failure this file exists to make impossible.
//
// So the criterion is the one that does not move: a module under `libs/` is a published library.
// It was already true before the family templates and it is still true after. That every one of
// them does chain up to `publishing.module-template.yaml` is worth asserting — but it belongs in a
// check that says so, not in the definition of the list.

import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

/** Where the published libraries live. Nothing outside it publishes. */
export const LIBS = "libs";

/** A publishable module: the directory holding a `module.yaml`, and the name the toolchain knows. */
export interface Module {
    /** Path relative to the repository root, e.g. `libs/data/stx-jpa/stx-jpa`. */
    readonly dir: string;
    /**
     * The module's name, which is its directory's name — `artifactId` is deliberately unset in the
     * publishing template, so this is also the Maven artifact name. `-m` takes this, never a path.
     */
    readonly name: string;
}

/** Recursively collects every module under `dir`, skipping build output. */
function walk(root: string, dir: string, found: Module[]): void {
    for (const entry of readdirSync(join(root, dir), { withFileTypes: true })) {
        if (entry.name === "build" || entry.name.startsWith(".")) continue;
        const child = `${dir}/${entry.name}`;
        if (entry.isDirectory()) walk(root, child, found);
        else if (entry.name === "module.yaml") found.push({ dir, name: dir.split("/").pop()! });
    }
}

/**
 * Every publishable module, sorted by name.
 *
 * Sorted because three callers compare this list against something, and an ordering that depends on
 * the filesystem makes those comparisons flap.
 */
export function modules(root: string = ROOT): Module[] {
    if (!statSync(join(root, LIBS), { throwIfNoEntry: false })?.isDirectory()) {
        throw new Error(`${LIBS}/ is not a directory under ${root}. Run this from the repository.`);
    }
    const found: Module[] = [];
    walk(root, LIBS, found);
    found.sort((a, b) => a.name.localeCompare(b.name));
    return found;
}

/** The shapes the callers need. */
export const FORMATS = {
    /** One name per line — for reading, and for `wc -l`. */
    names: (ms: Module[]) => ms.map((m) => m.name).join("\n"),
    /**
     * `-m stx-common -m stx-jpa …`, for `kotlin publish` / `kotlin build`.
     *
     * This is now the only publish form. Until toolchain 0.12.2 there was a second one, a list of
     * `:<module>:publishToMavenCentral` for `kotlin task`, because `kotlin publish mavenCentral`
     * refused on 0.12.0 — the built-in `mavenCentral` repository is resolve-only. 0.12.2 accepts
     * it, and the two forms were measured to write the same artifacts. `docs/releasing.md` has it.
     */
    "publish-args": (ms: Module[]) => ms.map((m) => `-m ${m.name}`).join(" "),
} as const;

export type Format = keyof typeof FORMATS;

if (import.meta.main) {
    const flag = process.argv[2]?.replace(/^--/, "") ?? "names";
    if (!(flag in FORMATS)) {
        console.error(`unknown format '${flag}'. One of: ${Object.keys(FORMATS).join(", ")}`);
        process.exit(2);
    }
    console.log(FORMATS[flag as Format](modules()));
}
