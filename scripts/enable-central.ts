// Turns on Maven Central publication and artifact signing in the shared publishing template.
//
// This runs in the release job and its edit is never committed, because the two settings are
// coupled and every committed combination breaks someone — both measured against the toolchain:
//
//   signArtifacts: true                  `./kotlin publish mavenLocal` fails with "Artifact signing
//                                        is enabled, but KOTLIN_TOOLCHAIN_SIGNING_KEY is not
//                                        provided". That local publish is what every contributor
//                                        does on every change under libs/, so it cannot need a PGP
//                                        key. (`build` and `test` are unaffected.)
//
//   mavenCentral + signArtifacts: false  the toolchain refuses the project at model-load time —
//                                        "Maven Central requires artifact signatures, but signing
//                                        is disabled" — so even `./kotlin build` fails.
//
// Committing neither breaks no one, which is why the template ships without them and this exists.
//
// `publishingMode` stays `manual`: the publish uploads a bundle and waits for a human to release it
// from the Central Portal. Sonatype does not let artifacts be removed once released.

import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const ROOT = fileURLToPath(new URL("../", import.meta.url));
const FILE = "publishing.module-template.yaml";

/** Inserted directly after the `publishSources:` line, at its indentation. */
const BLOCK: readonly string[] = [
    "signArtifacts: true",
    "mavenCentral:",
    "  enabled: true",
    "  publishingMode: manual",
];

const ANCHOR = /^([ \t]*)publishSources:[ \t]*true[ \t]*$/m;

/** @returns `true` if the block was inserted, `false` if the template already had it. */
export function enableCentral(root: string = ROOT): boolean {
    const path = root + FILE;
    const before = readFileSync(path, "utf8");

    if (/^[ \t]*mavenCentral:/m.test(before)) return false; // already on; idempotent

    const hits = before.match(new RegExp(ANCHOR.source, "gm")) ?? [];
    if (hits.length !== 1) {
        throw new Error(
            `${FILE}: expected exactly one \`publishSources: true\` to anchor to, found ${hits.length}. ` +
                `The template was reshaped and this script no longer knows where to insert.`,
        );
    }

    const after = before.replace(ANCHOR, (line: string, indent: string) =>
        [line, ...BLOCK.map((l) => indent + l)].join("\n"),
    );
    writeFileSync(path, after);
    return true;
}

if (import.meta.main) {
    console.log(enableCentral() ? `enabled Maven Central in ${FILE}` : `${FILE} already has it`);
}
