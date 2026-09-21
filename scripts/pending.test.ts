// Pins the two things a run with pending changesets has to say, because the failure they cover is
// silence: the release job skips every remaining step in this state, so whatever is not said here
// is not said at all.

import { describe, expect, test } from "bun:test";
import { parse } from "./changeset-guard.ts";
import { report } from "./pending.ts";

const real = (name: string) => parse(`${name}.md`, `---\n"${name}": patch\n---\n\nA change.\n`);
const blank = (name: string) => parse(`${name}.md`, `---\n---\n\nNothing published changes.\n`);

describe("report", () => {
    test("a pending release points at the pull request that performs it", () => {
        const r = report([real("stx-jpa"), blank("x")]);
        expect(r).toMatchObject({ real: 1, empty: 1, level: "notice" });
        expect(r.message).toContain("Version Packages");
    });

    test("only empty ones is a warning, because it can hide a blocked replay", () => {
        const r = report([blank("a"), blank("b")]);
        expect(r).toMatchObject({ real: 0, empty: 2, level: "warning" });
        expect(r.message).toContain("replay");
    });

    test("nothing pending is not the same as pending and empty", () => {
        // What `bun scripts/pending.ts` printed by hand on a clean develop: "0 pending
        // changeset(s), all empty". The workflow never calls it in this state, a reader does.
        const r = report([]);
        expect(r).toMatchObject({ real: 0, empty: 0, level: "notice" });
        expect(r.message).toContain("No changesets pending");
        expect(r.message).not.toContain("empty");
    });

    test("counts them, so the log says how many are in the way", () => {
        expect(report([blank("a"), blank("b"), blank("c")]).message).toContain("3 pending");
    });
});
