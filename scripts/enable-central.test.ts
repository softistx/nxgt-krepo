// Pins scripts/enable-central.ts. It edits the file that decides how 45 modules are published, in
// the one job where nobody is watching, so it gets the same treatment as sync-version.ts.

import { describe, expect, test } from "bun:test";
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { enableCentral } from "./enable-central.ts";

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

/** A throwaway root holding just the template the script edits. */
function fixture(template: string = TEMPLATE): string {
    const dir = mkdtempSync(join(tmpdir(), "enable-central-"));
    writeFileSync(join(dir, "publishing.module-template.yaml"), template);
    return dir + "/";
}

/** Runs `body` against a fresh fixture and removes it either way. */
function withTemplate(template: string, body: (root: string) => void): void {
    const root = fixture(template);
    try {
        body(root);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
}

const read = (root: string) => readFileSync(root + "publishing.module-template.yaml", "utf8");

describe("enabling Maven Central", () => {
    test("adds signing and the mavenCentral block at the anchor's indentation", () => {
        withTemplate(TEMPLATE, (root) => {
            expect(enableCentral(root)).toBe(true);
            expect(read(root)).toMatch(
                /^ {4}publishSources: true\n {4}signArtifacts: true\n {4}mavenCentral:\n {6}enabled: true\n {6}publishingMode: manual$/m,
            );
        });
    });

    test("leaves the rest of the template alone", () => {
        withTemplate(TEMPLATE, (root) => {
            enableCentral(root);
            expect(read(root)).toMatch(/group: io\.github\.softistx/);
            expect(read(root)).toMatch(/^ {4}version: 0\.1\.0$/m);
            expect(read(root)).toMatch(/url: https:\/\/github\.com\/softistx\/nxgt-krepo/);
            expect(read(root)).toMatch(/^ {2}- url: mavenLocal$/m);
        });
    });

    // `manual` is not a default worth drifting: Sonatype does not let a released artifact be removed.
    test("publishes in manual mode, never auto", () => {
        withTemplate(TEMPLATE, (root) => {
            enableCentral(root);
            expect(read(root)).toMatch(/publishingMode: manual/);
            expect(read(root)).not.toMatch(/publishingMode: auto/);
        });
    });

    test("is idempotent, and says it did nothing", () => {
        withTemplate(TEMPLATE, (root) => {
            enableCentral(root);
            const once = read(root);
            expect(enableCentral(root)).toBe(false);
            expect(read(root)).toBe(once);
        });
    });

    // A comment naming mavenCentral must not read as the setting being present — the committed
    // template has several, explaining why it is absent.
    test("is not fooled by a comment mentioning mavenCentral", () => {
        const commented = TEMPLATE.replace("    pom:", "    # mavenCentral: is added by CI\n    pom:");
        withTemplate(commented, (root) => {
            expect(enableCentral(root)).toBe(true);
            expect(read(root)).toMatch(/^ {4}mavenCentral:$/m);
        });
    });
});

describe("refusing to guess", () => {
    test("fails when publishSources is absent", () => {
        withTemplate(TEMPLATE.replace("    publishSources: true\n", ""), (root) => {
            expect(() => enableCentral(root)).toThrow(/found 0/);
        });
    });

    test("fails when a second publishSources appears", () => {
        withTemplate(TEMPLATE + "\n  other:\n    publishSources: true\n", (root) => {
            expect(() => enableCentral(root)).toThrow(/found 2/);
        });
    });
});
