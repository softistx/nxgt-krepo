// Pins readme-versions.ts: a README snippet follows its family's version, an artifact nobody
// publishes is left alone, and docs/ (where consuming.md pins old versions on purpose) is never read.

import { expect, test } from "bun:test";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { syncReadmeVersions } from "./readme-versions.ts";

const SNIPPET = (jpa: string, other: string) =>
    `implementation("io.github.softistx:stx-jpa:${jpa}")\nimplementation("io.github.softistx:stx-jpa-ktor:${jpa}")\n` +
    `implementation("io.github.softistx:elsewhere:${other}")\n`;

function fixture(): string {
    const root = mkdtempSync(join(tmpdir(), "readme-")) + "/";
    const dir = "libs/data/stx-jpa";
    for (const m of ["stx-jpa", "stx-jpa-ktor"]) {
        mkdirSync(`${root}${dir}/${m}`, { recursive: true });
        writeFileSync(`${root}${dir}/${m}/module.yaml`, "product: jvm/lib\n");
    }
    writeFileSync(`${root}${dir}/package.json`, JSON.stringify({ name: "stx-jpa", version: "0.3.1" }));
    writeFileSync(`${root}${dir}/stx-jpa.module-template.yaml`, "settings:\n  publishing:\n    version: 0.3.1\n");
    mkdirSync(`${root}docs`);
    writeFileSync(root + "README.md", SNIPPET("0.2.1", "9.9.9"));
    writeFileSync(`${root}${dir}/README.md`, "see `io.github.softistx:stx-jpa-ktor:0.1.0`\n");
    writeFileSync(root + "docs/consuming.md", SNIPPET("0.2.1", "9.9.9"));
    return root;
}

const read = (root: string, path: string) => readFileSync(root + path, "utf8");

test("rewrites a coordinate to the version of the family that owns the artifact", () => {
    const root = fixture();
    try {
        syncReadmeVersions(root);
        expect(read(root, "README.md")).toContain("stx-jpa:0.3.1");
        expect(read(root, "README.md")).toContain("stx-jpa-ktor:0.3.1");
        expect(read(root, "libs/data/stx-jpa/README.md")).toContain("stx-jpa-ktor:0.3.1");
    } finally {
        rmSync(root, { recursive: true });
    }
});

test("leaves an artifact no family publishes alone", () => {
    const root = fixture();
    try {
        syncReadmeVersions(root);
        expect(read(root, "README.md")).toContain("elsewhere:9.9.9");
    } finally {
        rmSync(root, { recursive: true });
    }
});

test("never touches docs/consuming.md", () => {
    const root = fixture();
    try {
        syncReadmeVersions(root);
        expect(read(root, "docs/consuming.md")).toBe(SNIPPET("0.2.1", "9.9.9"));
    } finally {
        rmSync(root, { recursive: true });
    }
});

test("--check reports the drift and writes nothing", () => {
    const root = fixture();
    try {
        expect(syncReadmeVersions(root, true).filter((w) => w.changed)).toHaveLength(2);
        expect(read(root, "README.md")).toBe(SNIPPET("0.2.1", "9.9.9"));
    } finally {
        rmSync(root, { recursive: true });
    }
});

test("a kmp platform artifact follows the version of its module's family", () => {
    const root = fixture();
    try {
        const platforms = ["jvm", "android", "iosarm64", "iossimulatorarm64", "iosx64"];
        writeFileSync(
            root + "README.md",
            platforms.map((t) => `io.github.softistx:stx-jpa-${t}:0.1.0\n`).join("") +
                "io.github.softistx:elsewhere-jvm:9.9.9\nio.github.softistx:stx-jpa-ktor-jvm:0.1.0\n",
        );
        syncReadmeVersions(root);
        for (const t of platforms) expect(read(root, "README.md")).toContain(`stx-jpa-${t}:0.3.1`);
        expect(read(root, "README.md")).toContain("stx-jpa-ktor-jvm:0.3.1");
        expect(read(root, "README.md")).toContain("elsewhere-jvm:9.9.9");
    } finally {
        rmSync(root, { recursive: true });
    }
});
