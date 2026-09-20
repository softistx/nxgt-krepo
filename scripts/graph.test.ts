// Pins the reading of a manifest and the two ways the graph can be wrong, because the whole point
// of the check is that neither failure shows up anywhere else: a missing edge builds, tests and
// publishes, and only stops releasing a library whose POM names a version that moved.

import { describe, expect, test } from "bun:test";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { type Family, families } from "./families.ts";
import { derived, mismatches } from "./graph.ts";

const family = (name: string, dir: string, deps: string[] = []): Family => ({
    name,
    dir,
    version: "0.2.1",
    modules: [{ dir: `${dir}/${name}`, name }],
    deps,
});

describe("mismatches", () => {
    const ALL = [
        family("stx-common", "libs/core/stx-common"),
        family("stx-jpa", "libs/data/stx-jpa", ["stx-common"]),
    ];

    test("says nothing when the declared graph is the derived one", () => {
        const graph = new Map([
            ["stx-common", new Set<string>()],
            ["stx-jpa", new Set(["stx-common"])],
        ]);
        expect(mismatches(ALL, graph)).toEqual([]);
    });

    test("catches an edge the manifests have and package.json does not", () => {
        const graph = new Map([
            ["stx-common", new Set<string>()],
            ["stx-jpa", new Set(["stx-common", "stx-ktor"])],
        ]);
        const [problem] = mismatches(ALL, graph);
        expect(problem).toContain("does not declare stx-ktor");
        expect(problem).toContain('"stx-ktor": "workspace:*"');
    });

    test("catches an edge package.json has and the manifests do not", () => {
        // The shape of a `compile-only` left in by hand: it is `provided` in the POM, so it must
        // not bump anything.
        const graph = new Map([
            ["stx-common", new Set<string>()],
            ["stx-jpa", new Set<string>()],
        ]);
        const [problem] = mismatches(ALL, graph);
        expect(problem).toContain("declares stx-common");
        expect(problem).toContain("Remove it");
    });
});

/** A throwaway repository: two families, four modules, one of each dependency shape. */
function fixture(): string {
    const root = mkdtempSync(join(tmpdir(), "graph-")) + "/";
    const write = (path: string, text: string) => {
        mkdirSync(join(root, path, ".."), { recursive: true });
        writeFileSync(join(root, path), text);
    };
    const pkg = (dir: string, name: string, deps: string[]) =>
        write(
            `${dir}/package.json`,
            JSON.stringify({
                name,
                version: "0.2.1",
                private: true,
                dependencies: Object.fromEntries(deps.map((d) => [d, "workspace:*"])),
            }),
        );

    pkg("libs/core/stx-common", "stx-common", []);
    write("libs/core/stx-common/module.yaml", "product: jvm/lib\n\ndependencies:\n  - $libs.kotlinx\n");

    pkg("libs/data/stx-jpa", "stx-jpa", ["stx-common"]);
    write(
        "libs/data/stx-jpa/stx-jpa/module.yaml",
        [
            "product: jvm/lib",
            "",
            "dependencies:",
            "  - //libs/core/stx-common: exported", // compile → propagates
            "",
            "test-dependencies:",
            "  - //libs/core/stx-testing", // absent from the POM → does not
            "",
        ].join("\n"),
    );
    write(
        "libs/data/stx-jpa/stx-jpa-ktor/module.yaml",
        [
            "product: jvm/lib",
            "",
            "dependencies:",
            "  - //libs/data/stx-jpa/stx-jpa: exported", // same family → not an edge
            "  - //libs/core/stx-common: compile-only", // provided → does not propagate
            "",
        ].join("\n"),
    );

    pkg("libs/core/stx-testing", "stx-testing", []);
    write("libs/core/stx-testing/module.yaml", "product: jvm/lib\n");
    return root;
}

describe("derived", () => {
    test("reads the runtime edges and only those", () => {
        const root = fixture();
        try {
            const graph = derived(root);
            // stx-jpa's `exported` edge counts; its test dependency and its `compile-only` do not.
            expect([...(graph.get("stx-jpa") ?? [])]).toEqual(["stx-common"]);
            expect([...(graph.get("stx-common") ?? [])]).toEqual([]);
            expect([...(graph.get("stx-testing") ?? [])]).toEqual([]);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("a family never depends on itself, however its modules are wired", () => {
        const root = fixture();
        try {
            expect(derived(root).get("stx-jpa")).not.toContain("stx-jpa");
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("the real repository's declared graph is its manifests' graph", () => {
        // The check itself, run against this repository — the one assertion that can catch a
        // package.json edited without its module.yaml, or the reverse.
        expect(mismatches(families(), derived())).toEqual([]);
    });
});
