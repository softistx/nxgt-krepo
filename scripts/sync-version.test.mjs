// Pins what sync-version.mjs does, because getting it wrong is silent: a release that moves one
// anchor and not the other leaves every example resolving a coordinate nobody published.
//
// This is a Node script, so it is a Node test. The repo's kotest FeatureSpec convention applies to
// Kotlin; `node --test` needs no dependency and runs in milliseconds.

import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { syncVersion } from "./sync-version.mjs";

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

function fixture({ template = TEMPLATE, catalog = CATALOG } = {}) {
    const dir = mkdtempSync(join(tmpdir(), "sync-version-"));
    writeFileSync(join(dir, "publishing.module-template.yaml"), template);
    writeFileSync(join(dir, "libs.versions.toml"), catalog);
    return dir + "/";
}

describe("propagating a version", () => {
    test("moves both anchors and nothing else", () => {
        const root = fixture();
        try {
            syncVersion("1.4.0", root);
            const template = readFileSync(root + "publishing.module-template.yaml", "utf8");
            const catalog = readFileSync(root + "libs.versions.toml", "utf8");

            assert.match(template, /^ {4}version: 1\.4\.0$/m);
            assert.match(catalog, /^stx = "1\.4\.0"$/m);

            // everything around the anchors survives verbatim
            assert.match(template, /group: io\.github\.softistx/);
            assert.match(catalog, /^ktor = "3\.5\.2"$/m);
            assert.match(catalog, /version\.ref = "stx"/);
            assert.equal(template.split("\n").length, TEMPLATE.split("\n").length);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("is idempotent", () => {
        const root = fixture();
        try {
            syncVersion("2.0.0", root);
            const once = readFileSync(root + "libs.versions.toml", "utf8");
            syncVersion("2.0.0", root);
            assert.equal(readFileSync(root + "libs.versions.toml", "utf8"), once);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("carries a prerelease and build metadata through", () => {
        const root = fixture();
        try {
            syncVersion("1.0.0-rc.1", root);
            assert.match(readFileSync(root + "libs.versions.toml", "utf8"), /^stx = "1\.0\.0-rc\.1"$/m);
            assert.match(
                readFileSync(root + "publishing.module-template.yaml", "utf8"),
                /^ {4}version: 1\.0\.0-rc\.1$/m,
            );
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });
});

describe("refusing to guess", () => {
    test("fails when the template's version line is gone", () => {
        const root = fixture({ template: TEMPLATE.replace("    version: 0.1.0\n", "") });
        try {
            assert.throws(() => syncVersion("1.0.0", root), /found 0/);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("fails when a second version line appears", () => {
        const root = fixture({ template: TEMPLATE + "\n  other:\n    version: 9.9.9\n" });
        try {
            assert.throws(() => syncVersion("1.0.0", root), /found 2/);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("fails when the catalog's stx ref is renamed", () => {
        const root = fixture({ catalog: CATALOG.replace('stx = "0.1.0"', 'stxx = "0.1.0"') });
        try {
            assert.throws(() => syncVersion("1.0.0", root), /libs\.versions\.toml.*found 0/s);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });
});
