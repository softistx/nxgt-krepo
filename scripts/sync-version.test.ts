// Pins what sync-version.ts does, because getting it wrong is silent: a release that moves a
// family's template and not its catalog key leaves every example resolving a coordinate nobody
// published. And a release that moves one family's key while matching another's would publish the
// wrong number under the right name, which no build would notice.
//
// The repo's kotest FeatureSpec convention applies to Kotlin. These are TypeScript, so they are
// `bun:test` — the runtime that already runs Changesets, with no build step and no extra dependency.

import { describe, expect, test } from "bun:test";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { structure, syncVersions } from "./sync-version.ts";

const SHARED = `settings:
  publishing:
    enabled: true
    group: io.github.softistx
    publishSources: true
`;

const catalog = (jpa: string, mongo: string) => `[versions]
# This repo's own published artifacts.
stx-jpa = "${jpa}"
stx-mongo = "${mongo}"
ktor = "3.5.2"

[libraries]
# A family name is also an alias down here — the anchor must not match this line.
stx-jpa = { module = "io.github.softistx:stx-jpa", version.ref = "stx-jpa" }
stx-mongo = { module = "io.github.softistx:stx-mongo", version.ref = "stx-mongo" }
`;

const template = (version: string) => `apply:
  - //publishing.module-template.yaml

settings:
  publishing:
    version: ${version}
`;

/**
 * A miniature of this repository: two families, one of them with a framework integration.
 *
 * `pkgVersion` is what `package.json` says, `tplVersion` and the catalog what the build reads — so
 * a test can make them disagree, which is the whole point of `--check`.
 */
function fixture(
    opts: {
        readonly pkg?: Record<string, string>;
        readonly tpl?: Record<string, string>;
        readonly cat?: readonly [string, string];
        readonly changelog?: Record<string, string>;
        readonly applyShared?: boolean;
        readonly dropApply?: boolean;
    } = {},
): string {
    const root = mkdtempSync(join(tmpdir(), "sync-")) + "/";
    const pkg: Record<string, string> = { "stx-jpa": "1.0.0", "stx-mongo": "2.0.0", ...opts.pkg };
    const tpl: Record<string, string> = { "stx-jpa": "1.0.0", "stx-mongo": "2.0.0", ...opts.tpl };

    writeFileSync(root + "publishing.module-template.yaml", SHARED);
    writeFileSync(root + "libs.versions.toml", catalog(...(opts.cat ?? ["1.0.0", "2.0.0"])));

    const modulesOf: Record<string, string[]> = {
        "stx-jpa": ["libs/data/stx-jpa/stx-jpa", "libs/data/stx-jpa/stx-jpa-ktor"],
        "stx-mongo": ["libs/data/stx-mongo"],
    };
    for (const [name, mods] of Object.entries(modulesOf)) {
        const dir = name === "stx-jpa" ? "libs/data/stx-jpa" : "libs/data/stx-mongo";
        mkdirSync(root + dir, { recursive: true });
        writeFileSync(
            `${root}${dir}/package.json`,
            JSON.stringify({ name, version: pkg[name], private: true }) + "\n",
        );
        writeFileSync(`${root}${dir}/${name}.module-template.yaml`, template(tpl[name]!));
        writeFileSync(`${root}${dir}/CHANGELOG.md`, opts.changelog?.[name] ?? `# ${name}\n`);
        for (const m of mods) {
            mkdirSync(root + m, { recursive: true });
            const applied = [
                ...(opts.dropApply && m.endsWith("-ktor") ? [] : [`  - //${dir}/${name}.module-template.yaml`]),
                ...(opts.applyShared && m.endsWith("-ktor") ? ["  - //publishing.module-template.yaml"] : []),
            ];
            writeFileSync(`${root}${m}/module.yaml`, `product: jvm/lib\n\napply:\n${applied.join("\n")}\n`);
        }
    }
    return root;
}

const read = (root: string, path: string) => readFileSync(root + path, "utf8");
const clean = (root: string) => rmSync(root, { recursive: true });

describe("syncVersions", () => {
    test("carries each family's own version into its own template", () => {
        const root = fixture({ pkg: { "stx-jpa": "1.4.0" } });
        try {
            syncVersions(root);
            expect(read(root, "libs/data/stx-jpa/stx-jpa.module-template.yaml")).toContain("version: 1.4.0");
            // The other family is untouched — that is the whole point of per-family versions.
            expect(read(root, "libs/data/stx-mongo/stx-mongo.module-template.yaml")).toContain("version: 2.0.0");
        } finally {
            clean(root);
        }
    });

    test("moves the catalog key of that family and no other", () => {
        const root = fixture({ pkg: { "stx-jpa": "1.4.0" } });
        try {
            syncVersions(root);
            const cat = read(root, "libs.versions.toml");
            expect(cat).toContain('stx-jpa = "1.4.0"');
            expect(cat).toContain('stx-mongo = "2.0.0"');
        } finally {
            clean(root);
        }
    });

    test("leaves the [libraries] alias of the same name alone", () => {
        const root = fixture({ pkg: { "stx-jpa": "1.4.0" } });
        try {
            syncVersions(root);
            expect(read(root, "libs.versions.toml")).toContain(
                'stx-jpa = { module = "io.github.softistx:stx-jpa", version.ref = "stx-jpa" }',
            );
        } finally {
            clean(root);
        }
    });

    test("is idempotent: a second run reports nothing changed", () => {
        const root = fixture({ pkg: { "stx-jpa": "1.4.0" } });
        try {
            syncVersions(root);
            expect(syncVersions(root).every((w) => !w.changed)).toBe(true);
        } finally {
            clean(root);
        }
    });

    test("carries a prerelease and build metadata through unharmed", () => {
        const root = fixture({ pkg: { "stx-jpa": "1.4.0-rc.1+build.7" } });
        try {
            syncVersions(root);
            expect(read(root, "libs/data/stx-jpa/stx-jpa.module-template.yaml")).toContain(
                "version: 1.4.0-rc.1+build.7",
            );
            expect(read(root, "libs.versions.toml")).toContain('stx-jpa = "1.4.0-rc.1+build.7"');
        } finally {
            clean(root);
        }
    });

    test("reports two anchors per family, named by file", () => {
        const root = fixture();
        try {
            expect(syncVersions(root).map((w) => w.file).sort()).toEqual([
                "libs.versions.toml",
                "libs.versions.toml",
                "libs/data/stx-jpa/stx-jpa.module-template.yaml",
                "libs/data/stx-mongo/stx-mongo.module-template.yaml",
            ]);
        } finally {
            clean(root);
        }
    });

    test("--check writes nothing but still reports the drift", () => {
        const root = fixture({ pkg: { "stx-jpa": "1.4.0" } });
        try {
            const written = syncVersions(root, true);
            expect(written.filter((w) => w.changed)).toHaveLength(2); // template + catalog key
            expect(read(root, "libs/data/stx-jpa/stx-jpa.module-template.yaml")).toContain("version: 1.0.0");
            expect(read(root, "libs.versions.toml")).toContain('stx-jpa = "1.0.0"');
        } finally {
            clean(root);
        }
    });

    test("refuses a template whose version line is gone, rather than writing nothing", () => {
        const root = fixture();
        try {
            writeFileSync(root + "libs/data/stx-jpa/stx-jpa.module-template.yaml", "apply: []\n");
            expect(() => syncVersions(root)).toThrow(/found 0/);
        } finally {
            clean(root);
        }
    });

    test("refuses a second version line in a family template", () => {
        const root = fixture();
        try {
            const path = "libs/data/stx-jpa/stx-jpa.module-template.yaml";
            writeFileSync(root + path, read(root, path) + "    version: 9.9.9\n");
            expect(() => syncVersions(root)).toThrow(/found 2/);
        } finally {
            clean(root);
        }
    });

    test("refuses a catalog whose family key was renamed", () => {
        const root = fixture();
        try {
            writeFileSync(
                root + "libs.versions.toml",
                read(root, "libs.versions.toml").replace('stx-jpa = "1.0.0"', 'jpa = "1.0.0"'),
            );
            expect(() => syncVersions(root)).toThrow(/libs\.versions\.toml.*found 0/s);
        } finally {
            clean(root);
        }
    });

    test("refuses a catalog with no [versions] table", () => {
        const root = fixture();
        try {
            writeFileSync(root + "libs.versions.toml", "[libraries]\n");
            expect(() => syncVersions(root)).toThrow(/no \[versions\] table/);
        } finally {
            clean(root);
        }
    });
});

describe("structure", () => {
    test("says nothing when every module goes through its family template", () => {
        const root = fixture();
        try {
            expect(structure(root)).toEqual([]);
        } finally {
            clean(root);
        }
    });

    test("catches a module wired to no family template — the one that publishes with no version", () => {
        const root = fixture({ dropApply: true });
        try {
            expect(structure(root)).toEqual([
                expect.stringContaining("libs/data/stx-jpa/stx-jpa-ktor/module.yaml does not apply"),
            ]);
        } finally {
            clean(root);
        }
    });

    test("catches a module applying the shared template alongside its family's — a conflict", () => {
        const root = fixture({ applyShared: true });
        try {
            expect(structure(root)).toEqual([
                expect.stringContaining("applies //publishing.module-template.yaml directly"),
            ]);
        } finally {
            clean(root);
        }
    });

    test("catches a CHANGELOG section for a version the family has not reached", () => {
        // How fourteen of these were left behind: a `changeset version` dry run reverted by halves,
        // package.json back at its old version and the CHANGELOG still carrying the new section.
        // The day the family really reaches it, Changesets prepends a second section with the same
        // heading and the release note takes whichever comes first.
        const root = fixture({ changelog: { "stx-jpa": "# stx-jpa\n\n## 1.1.0\n\n- never released\n" } });
        try {
            expect(structure(root)).toEqual([
                expect.stringContaining("has a '## 1.1.0' section, but stx-jpa is at 1.0.0"),
            ]);
        } finally {
            clean(root);
        }
    });

    test("says nothing about a section at or below the family's version", () => {
        const root = fixture({
            changelog: { "stx-jpa": "# stx-jpa\n\n## 1.0.0\n\n- released\n\n## 0.9.0\n\n- older\n" },
        });
        try {
            expect(structure(root)).toEqual([]);
        } finally {
            clean(root);
        }
    });

    test("catches a family with no CHANGELOG at all", () => {
        const root = fixture();
        try {
            rmSync(root + "libs/data/stx-mongo/CHANGELOG.md");
            expect(structure(root)).toEqual([expect.stringContaining("has no CHANGELOG.md")]);
        } finally {
            clean(root);
        }
    });

    test("catches a family template that does not chain to the shared one", () => {
        const root = fixture();
        try {
            writeFileSync(
                root + "libs/data/stx-jpa/stx-jpa.module-template.yaml",
                "settings:\n  publishing:\n    version: 1.0.0\n",
            );
            expect(structure(root)).toEqual([
                expect.stringContaining("stx-jpa.module-template.yaml does not apply"),
            ]);
        } finally {
            clean(root);
        }
    });
});

describe("this repository", () => {
    // The assertion the release depends on. It is what `--check` runs in CI, and running it here
    // too means a contributor sees it fail in `bun test` rather than in a release job.
    test("is wired correctly and its anchors agree with the families' package.json", () => {
        expect(structure()).toEqual([]);
        expect(syncVersions(undefined, true).filter((w) => w.changed)).toEqual([]);
    });

    test("has two anchors per family — 17 families, 34 anchors", () => {
        expect(syncVersions(undefined, true)).toHaveLength(34);
    });
});
