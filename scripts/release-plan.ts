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
 * Reads `changeset publish-plan --output`, keeping its batches.
 *
 * A batch is a set of families that can be published together because none of them depends on
 * another in the same batch, and every family they do depend on is in an earlier one. The release
 * publishes and tags one batch at a time, so the batches are the unit of "already up" that a
 * re-run gets to skip — flattening them would throw that away.
 */
export function batches(planPath: string, root: string = ROOT): Release[][] {
    const raw = JSON.parse(readFileSync(planPath, "utf8")) as { plan?: PlanEntry[][] };
    const byName = new Map(families(root).map((f) => [f.name, f]));

    return (raw.plan ?? []).map((batch) =>
        batch.map(({ name, version }) => {
            const family = byName.get(name);
            if (!family) {
                throw new Error(
                    `the publish plan names '${name}', which is not a family under libs/. Either ` +
                        `a family directory was renamed without its package.json, or the plan is ` +
                        `stale.`,
                );
            }
            return { family, version, tag: `${name}@${version}` };
        }),
    );
}

/** The same plan as one list, in the same order — for counting and for reading. */
export const releases = (planPath: string, root: string = ROOT): Release[] =>
    batches(planPath, root).flat();

/** The `./kotlin task` arguments that publish every module of every selected family. */
export const tasks = (rs: readonly Release[]) =>
    rs.flatMap((r) => r.family.modules.map((m) => `:${m.name}:publishToMavenCentral`)).join(" ");

if (import.meta.main) {
    const [path, format, which] = process.argv.slice(2);
    if (!path) {
        console.error(
            "usage: release-plan.ts <plan.json> [--tasks|--tags|--fields [batch] " +
                "|--count|--batch-count]",
        );
        process.exit(2);
    }
    const all = batches(path);

    // Every format but `--batch-count` takes an optional batch index, because the release publishes
    // one batch at a time and asks this script the same questions per batch as it used to ask once.
    // Without an index the answer covers the whole plan, which is what a human reading the plan and
    // `docs/releasing.md`'s by-hand procedure want.
    const i = which === undefined ? undefined : Number(which);
    if (i !== undefined && (!Number.isInteger(i) || i < 0 || i >= all.length)) {
        console.error(`'${which}' is not a batch of this plan — it has ${all.length}.`);
        process.exit(2);
    }
    const rs = i === undefined ? all.flat() : all[i]!;

    if (format === "--tasks") console.log(tasks(rs));
    else if (format === "--tags") console.log(rs.map((r) => r.tag).join("\n"));
    else if (format === "--count") console.log(String(rs.length));
    else if (format === "--batch-count") console.log(String(all.length));
    // `<name> <version> <dir>`, one per line, for the tagging and release-notes loops: they need the
    // family's directory to find its CHANGELOG, and looking that up from the name with a grep would
    // be one more thing that silently matches nothing.
    else if (format === "--fields") {
        console.log(rs.map((r) => `${r.family.name} ${r.version} ${r.family.dir}`).join("\n"));
    } else {
        all.forEach((batch, n) => {
            console.log(`batch ${n + 1} of ${all.length}`);
            for (const r of batch) {
                console.log(`  ${r.tag.padEnd(32)} ${r.family.modules.map((m) => m.name).join(", ")}`);
            }
        });
        const rels = all.flat();
        console.log(
            `\n${rels.length} families, ${rels.flatMap((r) => r.family.modules).length} artifacts, ` +
                `in ${all.length} ${all.length === 1 ? "batch" : "batches"}.`,
        );
    }
}
