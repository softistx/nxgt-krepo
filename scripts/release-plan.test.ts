// Pins how a publish plan becomes a list of modules to publish. The failure this guards is
// expensive and one-way: a family missing from the translation is a library that silently does not
// go out under its new version, and one added twice is an upload Maven Central refuses.

import { describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { batches, releases, tasks } from "./release-plan.ts";
import { section } from "./changelog-section.ts";

/** Writes a publish plan as `changeset publish-plan --output` writes one. */
function plan(batches: readonly (readonly [string, string])[][]): string {
    const file = join(mkdtempSync(join(tmpdir(), "plan-")), "plan.json");
    writeFileSync(
        file,
        JSON.stringify({
            version: 1,
            plan: batches.map((b) => b.map(([name, version]) => ({ kind: "tag-only", name, version }))),
        }),
    );
    return file;
}

describe("releases", () => {
    test("an empty plan releases nothing — the no-op run", () => {
        const file = plan([]);
        try {
            expect(releases(file)).toEqual([]);
        } finally {
            rmSync(file, { force: true });
        }
    });

    test("turns a family into its tag and its modules", () => {
        const file = plan([[["stx-jpa", "0.3.0"]]]);
        try {
            const [r] = releases(file);
            expect(r!.tag).toBe("stx-jpa@0.3.0");
            expect(r!.family.modules.map((m) => m.name)).toEqual([
                "stx-jpa",
                "stx-jpa-ktor",
                "stx-jpa-spring",
            ]);
        } finally {
            rmSync(file, { force: true });
        }
    });

    test("keeps the plan's dependency order across batches", () => {
        const file = plan([[["stx-common", "0.3.0"]], [["stx-ktor", "0.2.2"], ["stx-jpa", "0.2.2"]]]);
        try {
            expect(releases(file).map((r) => r.tag)).toEqual([
                "stx-common@0.3.0",
                "stx-ktor@0.2.2",
                "stx-jpa@0.2.2",
            ]);
        } finally {
            rmSync(file, { force: true });
        }
    });

    test("takes the version from the plan, not from the checkout", () => {
        // A stale working tree must not be able to publish under a version nobody planned.
        const file = plan([[["stx-mongo", "9.9.9"]]]);
        try {
            expect(releases(file)[0]!.tag).toBe("stx-mongo@9.9.9");
        } finally {
            rmSync(file, { force: true });
        }
    });

    test("refuses a plan naming something that is not a family", () => {
        const file = plan([[["stx-jpa-ktor", "0.3.0"]]]);
        try {
            expect(() => releases(file)).toThrow(/not a family under libs\//);
        } finally {
            rmSync(file, { force: true });
        }
    });

    test("keeps the batches, because they are the unit that gets published and tagged", () => {
        // Flattening these would be the whole difference between a failure costing one batch and a
        // failure costing the run: what is tagged after a batch is what a re-run no longer offers
        // to a repository that refuses a version it already holds.
        const file = plan([[["stx-common", "0.3.0"]], [["stx-ktor", "0.2.2"], ["stx-jpa", "0.2.2"]]]);
        try {
            expect(batches(file).map((b) => b.map((r) => r.tag))).toEqual([
                ["stx-common@0.3.0"],
                ["stx-ktor@0.2.2", "stx-jpa@0.2.2"],
            ]);
            // And the flat view is still the same list, in the same order.
            expect(releases(file).map((r) => r.tag)).toEqual([
                "stx-common@0.3.0",
                "stx-ktor@0.2.2",
                "stx-jpa@0.2.2",
            ]);
        } finally {
            rmSync(file, { force: true });
        }
    });

    test("an empty plan has no batches to loop over", () => {
        // `--batch-count` of 0 makes the workflow's `seq 0 -1` loop run zero times, which is the
        // only reason the publish step is allowed to be reached with nothing to do.
        const file = plan([]);
        try {
            expect(batches(file)).toEqual([]);
        } finally {
            rmSync(file, { force: true });
        }
    });

    test("produces one publish task per module, not per family", () => {
        const file = plan([[["stx-jpa", "0.3.0"], ["stx-common", "0.3.0"]]]);
        try {
            expect(tasks(releases(file))).toBe(
                ":stx-jpa:publishToMavenCentral :stx-jpa-ktor:publishToMavenCentral " +
                    ":stx-jpa-spring:publishToMavenCentral :stx-common:publishToMavenCentral",
            );
        } finally {
            rmSync(file, { force: true });
        }
    });
});

describe("section", () => {
    const CHANGELOG = `# stx-jpa

Preamble that is not a release.

## 0.3.0

### Minor Changes

- fetchEach on collections

## 0.2.1

- older

<!-- the footer every family CHANGELOG carries -->

---

This is the release line for \`io.github.softistx:stx-jpa\`.
`;

    test("takes one version's body, without its heading", () => {
        expect(section(CHANGELOG, "0.3.0")).toBe("### Minor Changes\n\n- fetchEach on collections");
    });

    test("stops at the next version rather than swallowing the rest", () => {
        expect(section(CHANGELOG, "0.3.0")).not.toContain("older");
    });

    test("reads the last section, which has no next heading to stop at", () => {
        expect(section(CHANGELOG, "0.2.1")).toBe("- older");
    });

    test("stops at the footer, which only the newest section is ever next to", () => {
        // The one that got through: the newest release has no `## ` heading after it, so the
        // footer went into `stx-material@0.2.2`'s tag message and the GitHub release body.
        const body = section(CHANGELOG, "0.2.1")!;
        expect(body).not.toContain("release line");
        expect(body).not.toContain("<!--");
    });

    test("answers null for a version that was never written, rather than an empty note", () => {
        expect(section(CHANGELOG, "0.9.9")).toBeNull();
    });

    test("does not mistake the title for a version heading", () => {
        expect(section(CHANGELOG, "stx-jpa")).toBeNull();
    });
});
