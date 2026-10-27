// Pins what families.ts derives from the tree: a family is a `libs/<role>/<name>` directory, its
// version and dependencies come from its package.json, and a tree that breaks the rules fails
// loudly instead of leaving a line that never bumps.

import { describe, expect, test } from "bun:test";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { families, templateOf } from "./families.ts";

type Spec = { modules: string[]; pkg?: object | null };

/** Builds a throwaway tree: each key is a family dir, each module a subdirectory (or "." for itself). */
function fixture(tree: Record<string, Spec>): string {
    const root = mkdtempSync(join(tmpdir(), "families-"));
    for (const [dir, spec] of Object.entries(tree)) {
        for (const m of spec.modules) {
            const d = m === "." ? join(root, dir) : join(root, dir, m);
            mkdirSync(d, { recursive: true });
            writeFileSync(join(d, "module.yaml"), "product: jvm/lib\n");
        }
        if (spec.pkg !== null) {
            writeFileSync(join(root, dir, "package.json"), JSON.stringify(spec.pkg));
        }
    }
    return root + "/";
}

const pkg = (name: string, extra: object = {}) => ({ name, version: "1.2.3", ...extra });

function withTree<T>(tree: Record<string, Spec>, run: (root: string) => T): T {
    const root = fixture(tree);
    try {
        return run(root);
    } finally {
        rmSync(root, { recursive: true });
    }
}

describe("families", () => {
    test("a family with integrations groups its modules, sorted, and a single library is its own module", () => {
        withTree(
            {
                "libs/data/stx-jpa": { modules: ["stx-jpa-spring", "stx-jpa", "stx-jpa-ktor"], pkg: pkg("stx-jpa") },
                "libs/core/stx-common": { modules: ["."], pkg: pkg("stx-common") },
            },
            (root) => {
                const found = families(root);
                expect(found.map((f) => f.name)).toEqual(["stx-common", "stx-jpa"]);
                const jpa = found[1]!;
                expect(jpa.dir).toBe("libs/data/stx-jpa");
                expect(jpa.version).toBe("1.2.3");
                expect(jpa.modules.map((m) => m.name)).toEqual(["stx-jpa", "stx-jpa-ktor", "stx-jpa-spring"]);
                expect(found[0]!.modules.map((m) => m.name)).toEqual(["stx-common"]);
            },
        );
    });

    test("deps are the package.json dependencies, sorted", () => {
        withTree(
            {
                "libs/data/stx-jpa": {
                    modules: ["."],
                    pkg: pkg("stx-jpa", { dependencies: { "stx-ktor": "workspace:*", "stx-common": "workspace:*" } }),
                },
            },
            (root) => expect(families(root)[0]!.deps).toEqual(["stx-common", "stx-ktor"]),
        );
    });

    test("no dependencies field means no deps", () => {
        withTree({ "libs/core/stx-common": { modules: ["."], pkg: pkg("stx-common") } }, (root) =>
            expect(families(root)[0]!.deps).toEqual([]),
        );
    });

    test("a family without a package.json is an error naming the directory", () => {
        withTree({ "libs/core/stx-common": { modules: ["."], pkg: null } }, (root) =>
            expect(() => families(root)).toThrow(/libs\/core\/stx-common\/package\.json is missing/),
        );
    });

    test("a package.json named differently from its directory is an error", () => {
        withTree({ "libs/core/stx-common": { modules: ["."], pkg: pkg("other") } }, (root) =>
            expect(() => families(root)).toThrow(/named 'other'/),
        );
    });

    test("templateOf points at the family's module template", () => {
        expect(templateOf({ dir: "libs/data/stx-jpa", name: "stx-jpa" })).toBe(
            "libs/data/stx-jpa/stx-jpa.module-template.yaml",
        );
    });

    test("the real tree derives without error and every family has a module", () => {
        const found = families();
        expect(found.length).toBeGreaterThan(0);
        for (const f of found) expect(f.modules.length).toBeGreaterThan(0);
    });
});
