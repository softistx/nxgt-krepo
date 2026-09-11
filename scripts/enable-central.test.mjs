// Pins scripts/enable-central.mjs. It edits the file that decides how 46 artifacts are published,
// in the one job where nobody is watching, so it gets the same treatment as sync-version.mjs.

import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { enableCentral } from "./enable-central.mjs";

const TEMPLATE = `settings:
  publishing:
    enabled: true
    group: io.github.softistx
    version: 0.1.0
    # A sources jar per platform.
    publishSources: true
    pom:
      url: https://github.com/softistx/nxgt-krepo

repositories:
  - url: mavenLocal
    publish: true
`;

function fixture(template = TEMPLATE) {
    const dir = mkdtempSync(join(tmpdir(), "enable-central-"));
    writeFileSync(join(dir, "publishing.module-template.yaml"), template);
    return dir + "/";
}

const read = (root) => readFileSync(root + "publishing.module-template.yaml", "utf8");

describe("enabling Maven Central", () => {
    test("adds signing and the mavenCentral block at the anchor's indentation", () => {
        const root = fixture();
        try {
            assert.equal(enableCentral(root), true);
            const s = read(root);
            assert.match(
                s,
                /^ {4}publishSources: true\n {4}signArtifacts: true\n {4}mavenCentral:\n {6}enabled: true\n {6}publishingMode: manual$/m,
            );
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("leaves the rest of the template alone", () => {
        const root = fixture();
        try {
            enableCentral(root);
            const s = read(root);
            assert.match(s, /group: io\.github\.softistx/);
            assert.match(s, /^ {4}version: 0\.1\.0$/m);
            assert.match(s, /url: https:\/\/github\.com\/softistx\/nxgt-krepo/);
            assert.match(s, /^ {2}- url: mavenLocal$/m);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    // `manual` is not a default worth drifting: Sonatype does not let a released artifact be removed.
    test("publishes in manual mode, never auto", () => {
        const root = fixture();
        try {
            enableCentral(root);
            assert.match(read(root), /publishingMode: manual/);
            assert.doesNotMatch(read(root), /publishingMode: auto/);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("is idempotent, and says it did nothing", () => {
        const root = fixture();
        try {
            enableCentral(root);
            const once = read(root);
            assert.equal(enableCentral(root), false);
            assert.equal(read(root), once);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    // A comment naming mavenCentral must not read as the setting being present — the committed
    // template has several, explaining why it is absent.
    test("is not fooled by a comment mentioning mavenCentral", () => {
        const root = fixture(TEMPLATE.replace("    pom:", "    # mavenCentral: is added by CI\n    pom:"));
        try {
            assert.equal(enableCentral(root), true);
            assert.match(read(root), /^ {4}mavenCentral:$/m);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });
});

describe("refusing to guess", () => {
    test("fails when publishSources is absent", () => {
        const root = fixture(TEMPLATE.replace("    publishSources: true\n", ""));
        try {
            assert.throws(() => enableCentral(root), /found 0/);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });

    test("fails when a second publishSources appears", () => {
        const root = fixture(TEMPLATE + "\n  other:\n    publishSources: true\n");
        try {
            assert.throws(() => enableCentral(root), /found 2/);
        } finally {
            rmSync(root, { recursive: true, force: true });
        }
    });
});
