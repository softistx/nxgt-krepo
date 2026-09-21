// Pins what modules.ts considers a published library, because getting it wrong is silent in the
// worst direction: a list that is too short means `kotlin publish` publishes fewer artifacts and
// still exits 0, and the examples then build against stale ones. There is no error to read.
//
// `bun:test` rather than kotest for the same reason as the other scripts here — this is TypeScript,
// run by the runtime that already runs Changesets.

import { describe, expect, test } from "bun:test";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { FORMATS, modules } from "./modules.ts";

/** Builds a throwaway tree of `module.yaml` files and returns its root, with a trailing slash. */
function fixture(dirs: readonly string[], extra: Record<string, string> = {}): string {
    const root = mkdtempSync(join(tmpdir(), "modules-"));
    for (const d of dirs) {
        mkdirSync(join(root, d), { recursive: true });
        writeFileSync(join(root, d, "module.yaml"), "product: jvm/lib\n");
    }
    for (const [path, body] of Object.entries(extra)) {
        mkdirSync(join(root, path, ".."), { recursive: true });
        writeFileSync(join(root, path), body);
    }
    return root + "/";
}

describe("modules", () => {
    test("finds a module at either depth, because both shapes exist under libs/", () => {
        const root = fixture(["libs/core/stx-common", "libs/data/stx-jpa/stx-jpa"]);
        try {
            expect(modules(root).map((m) => m.name)).toEqual(["stx-common", "stx-jpa"]);
        } finally {
            rmSync(root, { recursive: true });
        }
    });

    test("a module's name is its directory's name, not its path", () => {
        const root = fixture(["libs/data/stx-jpa/stx-jpa-ktor"]);
        try {
            expect(modules(root)).toEqual([
                { dir: "libs/data/stx-jpa/stx-jpa-ktor", name: "stx-jpa-ktor" },
            ]);
        } finally {
            rmSync(root, { recursive: true });
        }
    });

    test("ignores build output, which holds copies of the manifests", () => {
        const root = fixture(["libs/core/stx-common", "libs/core/stx-common/build/tasks/stx-ghost"]);
        try {
            expect(modules(root).map((m) => m.name)).toEqual(["stx-common"]);
        } finally {
            rmSync(root, { recursive: true });
        }
    });

    test("a family directory is not a module — it holds them, and has no manifest of its own", () => {
        const root = fixture(["libs/data/stx-jpa/stx-jpa"], {
            "libs/data/stx-jpa/stx-jpa.module-template.yaml": "settings:\n",
            "libs/data/stx-jpa/package.json": "{}\n",
        });
        try {
            expect(modules(root).map((m) => m.name)).toEqual(["stx-jpa"]);
        } finally {
            rmSync(root, { recursive: true });
        }
    });

    test("sorts by name, so that a caller comparing against this list does not flap", () => {
        const root = fixture([
            "libs/ui/stx-material",
            "libs/core/stx-common",
            "libs/data/stx-jpa/stx-jpa",
        ]);
        try {
            expect(modules(root).map((m) => m.name)).toEqual([
                "stx-common",
                "stx-jpa",
                "stx-material",
            ]);
        } finally {
            rmSync(root, { recursive: true });
        }
    });

    test("refuses a root with no libs/, rather than reporting that nothing publishes", () => {
        const root = mkdtempSync(join(tmpdir(), "modules-")) + "/";
        try {
            expect(() => modules(root)).toThrow(/libs\/ is not a directory/);
        } finally {
            rmSync(root, { recursive: true });
        }
    });
});

describe("formats", () => {
    const ms = [
        { dir: "libs/core/stx-common", name: "stx-common" },
        { dir: "libs/data/stx-jpa/stx-jpa", name: "stx-jpa" },
    ];

    test("names is one per line", () => {
        expect(FORMATS.names(ms)).toBe("stx-common\nstx-jpa");
    });

    test("publish-args repeats -m, which is how kotlin publish selects modules", () => {
        expect(FORMATS["publish-args"](ms)).toBe("-m stx-common -m stx-jpa");
    });

    test("tasks names the Portal upload task per module, which is what kotlin task runs", () => {
        expect(FORMATS.tasks(ms)).toBe(
            ":stx-common:publishToMavenCentral :stx-jpa:publishToMavenCentral",
        );
    });
});

describe("this repository", () => {
    // Not a tautology: it is the assertion that the replacement produces the same 45 the shell
    // one-liner produced, and it fails the day a library is added without a manifest — or added
    // outside libs/, where nothing would publish it.
    test("publishes 45 libraries, every one of them under libs/", () => {
        const found = modules();
        expect(found).toHaveLength(45);
        expect(found.every((m) => m.dir.startsWith("libs/"))).toBe(true);
    });

    test("every module's name is unique, which the toolchain requires of the whole project", () => {
        const names = modules().map((m) => m.name);
        expect(new Set(names).size).toBe(names.length);
    });
});
