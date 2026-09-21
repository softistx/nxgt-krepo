// The propagation graph, checked against the manifests that actually produce it.
//
// A family's `package.json` `dependencies` are what make a bump travel: Changesets bumps a
// dependent when the dependency's new version falls outside the recorded range, and `workspace:*`
// resolves to the exact old version, so every bump does. That list is written by hand, and nothing
// about it is checked by building — a missing edge builds, tests and publishes perfectly well. It
// just quietly stops releasing a library whose POM names a version that moved.
//
// So it is checked here, against the `module.yaml` files the POMs are generated from. The mapping
// was measured on published artifacts, not assumed:
//
//     dependencies:       - //libs/x/y         → POM scope `runtime`   propagates
//     dependencies:       - //libs/x/y: exported → POM scope `compile`  propagates
//     dependencies:       - //libs/x/y: compile-only → scope `provided` does NOT
//     test-dependencies:  - //libs/x/y         → absent from the POM    does NOT
//
// `provided` and absent cannot reach a consumer transitively — the consumer supplies them — so
// neither obliges a dependent to be republished. That was a decision, and this is where it is
// enforced rather than remembered.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { type Family, families } from "./families.ts";
import { modules } from "./modules.ts";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

/** A `- //libs/<role>/<family>[/<module>][: <marker>]` line, however it is indented. */
const DEPENDENCY = /^\s*-\s+\/\/(libs\/[^\s:]+?)(?:\s*:\s*(\S+))?\s*$/;

/** A top-level key of a `module.yaml`, which is what says whether a list is runtime or test. */
const SECTION = /^([a-z-]+)(@\w+)?:/;

/**
 * The families `path`'s manifest depends on at runtime, as the POM will record them.
 *
 * Deliberately not a YAML parser. The lines this has to read are one shape, and a manifest that
 * does not match it is a manifest nobody here has seen — worth reporting rather than accommodating.
 */
function manifestDeps(text: string, familyOf: (dir: string) => string | undefined): Set<string> {
    const found = new Set<string>();
    let section = "";
    for (const line of text.split("\n")) {
        const key = line.match(SECTION);
        if (key) section = key[1] ?? "";
        if (section !== "dependencies") continue;

        const dep = line.match(DEPENDENCY);
        if (!dep) continue;
        const [, path = "", marker] = dep;
        // `apply:` also names `//libs/...` paths; it is a different section, but a template applied
        // from inside `dependencies:` would be a manifest error worth not silently counting.
        if (path.endsWith(".yaml")) continue;
        if (marker === "compile-only") continue;

        const name = familyOf(path);
        if (name) found.add(name);
    }
    return found;
}

/** Each family's runtime dependencies on other families, derived from its modules' manifests. */
export function derived(root: string = ROOT): Map<string, Set<string>> {
    const all = families(root);
    const byDir = new Map(all.map((f) => [f.dir, f.name]));
    // A dependency path names a module directory (`libs/data/stx-jpa/stx-jpa`) or, for a family
    // that is one library, the family directory itself (`libs/core/stx-common`).
    const familyOf = (dir: string) =>
        byDir.get(dir) ?? byDir.get(dir.split("/").slice(0, 3).join("/"));

    const graph = new Map(all.map((f) => [f.name, new Set<string>()]));
    for (const m of modules(root)) {
        const mine = familyOf(m.dir);
        if (!mine) continue;
        const text = readFileSync(`${root}${m.dir}/module.yaml`, "utf8");
        for (const theirs of manifestDeps(text, familyOf)) {
            if (theirs !== mine) graph.get(mine)?.add(theirs);
        }
    }
    return graph;
}

/** Where the declared graph and the manifests disagree. Empty means they agree. */
export function mismatches(all: readonly Family[], graph: ReadonlyMap<string, Set<string>>): string[] {
    const problems: string[] = [];
    for (const f of all) {
        const actual = graph.get(f.name) ?? new Set<string>();
        const declared = new Set(f.deps);
        const missing = [...actual].filter((d) => !declared.has(d)).sort();
        const extra = [...declared].filter((d) => !actual.has(d)).sort();

        if (missing.length) {
            problems.push(
                `${f.dir}/package.json does not declare ${missing.join(", ")}, but a module of ` +
                    `${f.name} depends on ${missing.length === 1 ? "it" : "them"} at runtime — so ` +
                    `the POM names a version that can move without ${f.name} being released. Add ` +
                    `${missing.map((d) => `"${d}": "workspace:*"`).join(", ")} to its dependencies.`,
            );
        }
        if (extra.length) {
            problems.push(
                `${f.dir}/package.json declares ${extra.join(", ")}, but no module of ${f.name} ` +
                    `depends on ${extra.length === 1 ? "it" : "them"} at runtime. A ` +
                    `\`compile-only\` or a test dependency is \`provided\` or absent in the POM, ` +
                    `so it must not bump ${f.name}. Remove it.`,
            );
        }
    }
    return problems;
}

if (import.meta.main) {
    const all = families();
    const graph = derived();

    if (process.argv.includes("--check")) {
        const problems = mismatches(all, graph);
        for (const p of problems) console.error(`::error::${p}`);
        if (problems.length) process.exit(1);
        const edges = [...graph.values()].reduce((n, s) => n + s.size, 0);
        console.log(`${all.length} families, ${edges} runtime edges, all declared.`);
    } else {
        for (const f of all) {
            const deps = [...(graph.get(f.name) ?? [])].sort();
            console.log(`${f.name.padEnd(24)} ${deps.join(", ") || "—"}`);
        }
    }
}
