// What a release actually publishes: the families whose version has moved past their tag, and the
// modules that belong to them.
//
// The selection is not computed here. `changeset publish-plan` already answers exactly this
// question — which packages are ahead of their git tag — and answers it in dependency order, in
// batches. All this file does is read that answer and translate family names into the module names
// the toolchain publishes, because Changesets knows about `stx-jpa` the release line and the
// toolchain knows about `stx-jpa`, `stx-jpa-ktor` and `stx-jpa-spring` the artifacts.
//
// Everything in it is `kind: "tag-only"`: these packages are `private`, so Changesets never tries to
// put them on npm. The tag is the whole point — it is what records that a version went out, and
// therefore what keeps the next run from publishing it again. Maven Central does not allow a
// version to be replaced, so publishing twice is not a retry, it is a failed job.
//
// **A family with no tag at all looks new.** Immediately after the migration to per-family versions
// no `<family>@<version>` tag existed, so every family was in the plan and a release would have
// tried to republish 0.2.1 over 0.2.1 — seventeen times. The bootstrap tags are what prevent that;
// `docs/releasing.md` has the procedure.

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { type Family, families } from "./families.ts";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

/** One entry of `changeset publish-plan --output`. */
interface PlanEntry {
    /** `tag-only` for a private package, `publish` for one that would go to npm. */
    readonly kind: string;
    readonly name: string;
    readonly version: string;
}

/** A family this run releases, with the modules the toolchain has to publish for it. */
export interface Release {
    readonly family: Family;
    /** The version being released — from the plan, not from disk, so a stale checkout cannot lie. */
    readonly version: string;
    /** `<name>@<version>`, the git tag Changesets will create for it. */
    readonly tag: string;
}

/**
 * Reads `changeset publish-plan --output`, flattening its batches.
 *
 * The batches are in dependency order and that order is kept, because it is free and it makes a
 * partially failed run easier to read: everything before the failure is a family whose
 * dependencies were already uploaded.
 */
export function releases(planPath: string, root: string = ROOT): Release[] {
    const raw = JSON.parse(readFileSync(planPath, "utf8")) as { plan?: PlanEntry[][] };
    const byName = new Map(families(root).map((f) => [f.name, f]));

    return (raw.plan ?? []).flat().map(({ name, version }) => {
        const family = byName.get(name);
        if (!family) {
            throw new Error(
                `the publish plan names '${name}', which is not a family under libs/. Either a ` +
                    `family directory was renamed without its package.json, or the plan is stale.`,
            );
        }
        return { family, version, tag: `${name}@${version}` };
    });
}

/** The `./kotlin task` arguments that publish every module of every selected family. */
export const tasks = (rs: readonly Release[]) =>
    rs.flatMap((r) => r.family.modules.map((m) => `:${m.name}:publishToMavenCentral`)).join(" ");

if (import.meta.main) {
    const [path, format] = process.argv.slice(2);
    if (!path) {
        console.error("usage: release-plan.ts <plan.json> [--tasks|--tags|--count|--fields]");
        process.exit(2);
    }
    const rs = releases(path);

    if (format === "--tasks") console.log(tasks(rs));
    else if (format === "--tags") console.log(rs.map((r) => r.tag).join("\n"));
    else if (format === "--count") console.log(String(rs.length));
    // `<name> <version> <dir>`, one per line, for the release-notes loop: it needs the family's
    // directory to find its CHANGELOG, and looking that up from the name with a grep would be one
    // more thing that silently matches nothing.
    else if (format === "--fields") {
        console.log(rs.map((r) => `${r.family.name} ${r.version} ${r.family.dir}`).join("\n"));
    }
    else {
        for (const r of rs) {
            console.log(`${r.tag.padEnd(32)} ${r.family.modules.map((m) => m.name).join(", ")}`);
        }
        console.log(`\n${rs.length} families, ${rs.flatMap((r) => r.family.modules).length} artifacts.`);
    }
}
