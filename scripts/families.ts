// A library family: the unit a version belongs to.
//
// `stx-jpa`, `stx-jpa-ktor` and `stx-jpa-spring` are three artifacts and one release line. They
// ship together, they are tested together, and a consumer who takes one of them takes the matching
// others — so they carry one version between them rather than three that drift for no reason.
//
// A family is a directory `libs/<role>/<name>/`, and it holds exactly three things that matter
// here: the modules it publishes, a `package.json` that Changesets versions, and a
// `<name>.module-template.yaml` that carries that version into the build. A family with framework
// integrations holds its modules as subdirectories; a family that is one library *is* that
// module's directory. The rule is the same either way, which is why nothing below special-cases it.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { type Module, modules } from "./modules.ts";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

/** What a family's `package.json` says. Only the fields this repository writes or reads. */
interface Manifest {
    readonly name: string;
    readonly version: string;
    readonly dependencies?: Readonly<Record<string, string>>;
}

export interface Family {
    /** The family's name, which is its directory's name and its Changesets package name. */
    readonly name: string;
    /** Path relative to the repository root, e.g. `libs/data/stx-jpa`. */
    readonly dir: string;
    /** The version every module in it publishes under. Changesets owns this. */
    readonly version: string;
    /** Its published modules, sorted by name. Never empty. */
    readonly modules: readonly Module[];
    /**
     * The families it depends on at runtime, sorted — the `dependencies` of its `package.json`.
     *
     * These are what make a bump propagate: Changesets bumps a dependent when the dependency's new
     * version falls outside the recorded range, and `workspace:*` resolves to the exact old
     * version, so every bump does. `compile-only` and `test-dependencies` are deliberately absent:
     * the first is `provided` in the POM and the second is not in it at all, so neither obliges a
     * dependent to be republished. `docs/releasing.md` has the reasoning.
     */
    readonly deps: readonly string[];
}

/** The template that carries a family's version into the build, relative to the repository root. */
export const templateOf = (f: Pick<Family, "dir" | "name">) => `${f.dir}/${f.name}.module-template.yaml`;

/** A family directory is the first three segments of a module's path: `libs/<role>/<name>`. */
const familyDirOf = (m: Module) => m.dir.split("/").slice(0, 3).join("/");

/**
 * Every family, sorted by name, each with the modules it publishes.
 *
 * Derived from the filesystem rather than from a list, which is what let the move to role folders
 * change no coordinate and touch no script.
 */
export function families(root: string = ROOT): Family[] {
    const byDir = new Map<string, Module[]>();
    for (const m of modules(root)) {
        const dir = familyDirOf(m);
        const group = byDir.get(dir);
        if (group) group.push(m);
        else byDir.set(dir, [m]);
    }

    const found = [...byDir].map(([dir, mods]) => {
        const path = `${root}${dir}/package.json`;
        let manifest: Manifest;
        try {
            manifest = JSON.parse(readFileSync(path, "utf8")) as Manifest;
        } catch {
            throw new Error(
                `${dir}/package.json is missing or unreadable, but ${dir} publishes ` +
                    `${mods.map((m) => m.name).join(", ")}. Every family needs one: it is where ` +
                    `Changesets keeps the version, and without it nothing bumps that line.`,
            );
        }
        if (manifest.name !== dir.split("/").pop()) {
            throw new Error(
                `${dir}/package.json is named '${manifest.name}', but its directory is ` +
                    `'${dir.split("/").pop()}'. A changeset names the package, a module.yaml names ` +
                    `the directory, and nothing would connect the two.`,
            );
        }
        return {
            name: manifest.name,
            dir,
            version: manifest.version,
            modules: mods,
            deps: Object.keys(manifest.dependencies ?? {}).sort(),
        };
    });

    found.sort((a, b) => a.name.localeCompare(b.name));
    return found;
}
