// Pins what sync-version.ts does, because getting it wrong is silent: a release that moves one
// anchor and not the other leaves every example resolving a coordinate nobody published.
//
// The repo's kotest FeatureSpec convention applies to Kotlin. These are TypeScript, so they are
// `bun:test` — the runtime that already runs Changesets, with no build step and no extra dependency.

import { describe, expect, test } from "bun:test";
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { syncVersion } from "./sync-version.ts";

const TEMPLATE = `settings:
  publishing:
    enabled: true
    group: io.github.softistx
    version: 0.1.0
    # artifactId is deliberately absent.

repositories:
  - url: mavenLocal
    publish: true
`;

const CATALOG = `[versions]
# This repo's own published artifacts.
stx = "0.1.0"
ktor = "3.5.2"

[libraries]
stx-jpa = { module = "io.github.softistx:stx-jpa", version.ref = "stx" }
`;

interface Fixture {
    readonly template?: string;
    readonly catalog?: string;
}

/** A throwaway root holding just the two files the script edits. */
function fixture({ template = TEMPLATE, catalog = CATALOG }: Fixture = {}): string {
    const dir = mkdtempSync(join(tmpdir(), "sync-version-"));
    writeFileSync(join(dir, "publishing.module-template.yaml"), template);
    writeFileSync(join(dir, "libs.versions.toml"), catalog);
    return dir + "/";
}

/** Runs `body` against a fresh fixture and removes it either way. */
function withFixture(files: Fixture, body: (root: string) => void): void {
    const root = fixture(files);
    try {
        body(root);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
}

const template = (root: string) => readFileSync(root + "publishing.module-template.yaml", "utf8");
const catalog = (root: string) => readFileSync(root + "libs.versions.toml", "utf8");

describe("propagating a version", () => {
    test("moves both anchors and nothing else", () => {
        withFixture({}, (root) => {
            syncVersion("1.4.0", root);

            expect(template(root)).toMatch(/^ {4}version: 1\.4\.0$/m);
            expect(catalog(root)).toMatch(/^stx = "1\.4\.0"$/m);

            // everything around the anchors survives verbatim
            expect(template(root)).toMatch(/group: io\.github\.softistx/);
            expect(catalog(root)).toMatch(/^ktor = "3\.5\.2"$/m);
            expect(catalog(root)).toMatch(/version\.ref = "stx"/);
            expect(template(root).split("\n").length).toBe(TEMPLATE.split("\n").length);
        });
    });

    test("is idempotent", () => {
        withFixture({}, (root) => {
            syncVersion("2.0.0", root);
            const once = catalog(root);
            syncVersion("2.0.0", root);
            expect(catalog(root)).toBe(once);
        });
    });

    test("carries a prerelease and build metadata through", () => {
        withFixture({}, (root) => {
            syncVersion("1.0.0-rc.1", root);
            expect(catalog(root)).toMatch(/^stx = "1\.0\.0-rc\.1"$/m);
            expect(template(root)).toMatch(/^ {4}version: 1\.0\.0-rc\.1$/m);
        });
    });

    test("reports which files it changed", () => {
        withFixture({}, (root) => {
            expect(syncVersion("3.1.0", root)).toEqual([
                { file: "publishing.module-template.yaml", changed: true },
                { file: "libs.versions.toml", changed: true },
            ]);
            expect(syncVersion("3.1.0", root)).toEqual([
                { file: "publishing.module-template.yaml", changed: false },
                { file: "libs.versions.toml", changed: false },
            ]);
        });
    });
});

describe("refusing to guess", () => {
    test("fails when the template's version line is gone", () => {
        withFixture({ template: TEMPLATE.replace("    version: 0.1.0\n", "") }, (root) => {
            expect(() => syncVersion("1.0.0", root)).toThrow(/found 0/);
        });
    });

    test("fails when a second version line appears", () => {
        withFixture({ template: TEMPLATE + "\n  other:\n    version: 9.9.9\n" }, (root) => {
            expect(() => syncVersion("1.0.0", root)).toThrow(/found 2/);
        });
    });

    test("fails when the catalog's stx ref is renamed", () => {
        withFixture({ catalog: CATALOG.replace('stx = "0.1.0"', 'stxx = "0.1.0"') }, (root) => {
            expect(() => syncVersion("1.0.0", root)).toThrow(/libs\.versions\.toml.*found 0/s);
        });
    });
});
