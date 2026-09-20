// Pins the guard, because it is the one place a contributor is told they got something wrong, and
// the message is all they will see. A guard that passes when it should fail lets a library change
// ship under a version that did not move; one that fails when it should pass teaches people to
// route around it.

import { describe, expect, test } from "bun:test";
import type { Family } from "./families.ts";
import { type Changeset, judge, parse } from "./changeset-guard.ts";

const family = (name: string, dir: string, deps: string[] = []): Family => ({
    name,
    dir,
    version: "0.2.1",
    modules: [{ dir: `${dir}/${name}`, name }],
    deps,
});

const ALL: Family[] = [
    family("stx-common", "libs/core/stx-common"),
    family("stx-ktor", "libs/core/stx-ktor", ["stx-common"]),
    family("stx-jpa", "libs/data/stx-jpa", ["stx-common", "stx-ktor"]),
    family("stx-mongo", "libs/data/stx-mongo", ["stx-common", "stx-ktor"]),
];

const cs = (file: string, bumps: Record<string, string>): Changeset =>
    parse(
        file,
        `---\n${Object.entries(bumps)
            .map(([k, v]) => `"${k}": ${v}`)
            .join("\n")}\n---\n\nprose\n`,
    );

const empty = parse(".changeset/empty.md", "---\n---\n\nnothing published changes\n");

describe("parse", () => {
    test("reads the names and bumps Changesets writes", () => {
        expect([...cs("x.md", { "stx-jpa": "minor" }).bumps]).toEqual([["stx-jpa", "minor"]]);
    });

    test("an empty front matter is a changeset with no bumps, not an error", () => {
        expect(empty.bumps.size).toBe(0);
    });

    test("refuses a file with no front matter at all", () => {
        expect(() => parse("x.md", "just prose\n")).toThrow(/no --- front matter/);
    });

    test("refuses a bump that is not major, minor or patch", () => {
        expect(() => parse("x.md", '---\n"stx-jpa": huge\n---\n')).toThrow(/expected major, minor or patch/);
    });
});

describe("judge", () => {
    test("says nothing when the pull request touches no library", () => {
        expect(judge(["docs/releasing.md", "scripts/modules.ts"], [], ALL)).toEqual({
            errors: [],
            warnings: [],
        });
    });

    test("fails a library change that carries no changeset, naming the family", () => {
        const { errors } = judge(["libs/data/stx-jpa/stx-jpa/src/Jpa.kt"], [], ALL);
        expect(errors).toHaveLength(1);
        expect(errors[0]).toContain("stx-jpa");
        expect(errors[0]).toContain("bun changeset");
    });

    test("passes when the touched family is the one declared", () => {
        const changed = ["libs/data/stx-jpa/stx-jpa/src/Jpa.kt"];
        expect(judge(changed, [cs("x.md", { "stx-jpa": "minor" })], ALL).errors).toEqual([]);
    });

    test("fails when a changeset names a different family than the one changed", () => {
        const changed = ["libs/data/stx-jpa/stx-jpa/src/Jpa.kt"];
        const { errors } = judge(changed, [cs("x.md", { "stx-mongo": "patch" })], ALL);
        expect(errors).toEqual([expect.stringContaining("changed but not declared")]);
        expect(errors[0]).toContain("stx-jpa");
    });

    test("catches the second of two touched families", () => {
        const changed = [
            "libs/data/stx-jpa/stx-jpa/src/Jpa.kt",
            "libs/data/stx-mongo/stx-mongo/src/Mongo.kt",
        ];
        const { errors } = judge(changed, [cs("x.md", { "stx-jpa": "patch" })], ALL);
        expect(errors[0]).toContain("stx-mongo");
        expect(errors[0]).not.toContain("stx-jpa,");
    });

    test("an integration module counts as its family", () => {
        // `libs/data/stx-jpa/stx-jpa-ktor` is stx-jpa's, not a family of its own.
        const changed = ["libs/data/stx-jpa/stx-jpa-ktor/src/Plugin.kt"];
        expect(judge(changed, [cs("x.md", { "stx-jpa": "patch" })], ALL).errors).toEqual([]);
    });

    test("an empty changeset covers everything, because it says nothing is published", () => {
        const changed = [
            "libs/data/stx-jpa/stx-jpa/test/JpaTest.kt",
            "libs/data/stx-mongo/stx-mongo/test/MongoTest.kt",
        ];
        expect(judge(changed, [empty], ALL).errors).toEqual([]);
    });

    test("refuses a name that is not a family — the repository itself, most likely", () => {
        const { errors } = judge([], [cs("x.md", { "nxgt-krepo": "patch" })], ALL);
        expect(errors).toEqual([expect.stringContaining("'nxgt-krepo', which is not a library family")]);
    });

    test("refuses an artifact name where a family name belongs", () => {
        const { errors } = judge([], [cs("x.md", { "stx-jpa-ktor": "patch" })], ALL);
        expect(errors).toEqual([expect.stringContaining("'stx-jpa-ktor', which is not a library family")]);
    });

    test("warns when a major leaves its dependents unnamed, and does not fail", () => {
        const changed = ["libs/core/stx-common/src/Page.kt"];
        const { errors, warnings } = judge(changed, [cs("x.md", { "stx-common": "major" })], ALL);
        expect(errors).toEqual([]);
        expect(warnings).toHaveLength(1);
        expect(warnings[0]).toContain("stx-jpa");
        expect(warnings[0]).toContain("stx-ktor");
        expect(warnings[0]).toContain("stx-mongo");
    });

    test("stays quiet when a major names its dependents", () => {
        const changed = ["libs/core/stx-common/src/Page.kt"];
        const declared = cs("x.md", {
            "stx-common": "major",
            "stx-ktor": "major",
            "stx-jpa": "major",
            "stx-mongo": "major",
        });
        expect(judge(changed, [declared], ALL).warnings).toEqual([]);
    });

    test("does not warn about a minor, which Changesets propagates honestly enough", () => {
        const changed = ["libs/core/stx-common/src/Page.kt"];
        expect(judge(changed, [cs("x.md", { "stx-common": "minor" })], ALL).warnings).toEqual([]);
    });

    test("a family with no dependents can be a major in peace", () => {
        const changed = ["libs/data/stx-mongo/stx-mongo/src/Mongo.kt"];
        expect(judge(changed, [cs("x.md", { "stx-mongo": "major" })], ALL).warnings).toEqual([]);
    });
});
