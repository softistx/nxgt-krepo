// One version's section of a CHANGELOG, which is what a GitHub release's body is made of.
//
// This used to be an `awk` one-liner inside the release workflow. It moved out for one reason: it
// is the only part of the release that produces something a human reads, and it had no test. An
// `awk` that silently matches nothing writes an empty release note and the job still succeeds.

import { readFileSync } from "node:fs";

/**
 * The body of `## <version>` in `text`, without the heading, trimmed.
 *
 * A section ends at the next `## ` heading — or at the footer, which every family `CHANGELOG.md`
 * carries and which opens with an HTML comment in the first column. That second stop is not
 * decoration: the *newest* section has no heading after it, so without it the newest release — the
 * one always being cut — took the footer with it into the tag message and the GitHub release body.
 * Measured on `stx-material@0.2.2`, the first release under this circuit.
 *
 * @returns `null` when the changelog has no such section — which is a real answer, not a failure: a
 * family bumped only because a dependency moved has a section, but a caller may ask for a version
 * that was never written, and an empty string would hide the difference.
 */
export function section(text: string, version: string): string | null {
    const lines = text.split("\n");
    const start = lines.findIndex((l) => l.trimEnd() === `## ${version}`);
    if (start < 0) return null;

    const rest = lines.slice(start + 1);
    const end = rest.findIndex((l) => l.startsWith("## ") || l.startsWith("<!--"));
    const body = (end < 0 ? rest : rest.slice(0, end)).join("\n").trim();
    return body;
}

if (import.meta.main) {
    const [path, version] = process.argv.slice(2);
    if (!path || !version) {
        console.error("usage: changelog-section.ts <CHANGELOG.md> <version>");
        process.exit(2);
    }
    const body = section(readFileSync(path, "utf8"), version);
    if (body === null) {
        console.error(`${path}: no '## ${version}' section.`);
        process.exit(1);
    }
    console.log(body);
}
