// What is waiting in `.changeset/`, from the release job's point of view.
//
// The job has three outcomes and, until this existed, only two of them said anything.
// `changesets/action` reports `has-changesets: true` whenever `.changeset/` holds anything at all,
// and the publish path runs only when that is false. But when every pending changeset is *empty*
// the action opens no "Version Packages" pull request either — it prints "All changesets are empty.
// Not creating PR" and stops. Every step after it is then skipped, including the one whose whole
// job is to say that nothing was released, and the run ends green having explained nothing.
//
// That is harmless while nothing is waiting to go out. It is not harmless when something is: a
// release that failed halfway is replayed by re-running the job, and an unrelated empty changeset
// sitting in the directory turns that replay into the same silent no-op.

import { type Changeset, changesets, isEmpty } from "./changeset-guard.ts";

/** What a run with pending changesets should tell whoever reads its log. */
export interface Report {
    readonly real: number;
    readonly empty: number;
    /** `warning` for the case that can hide a blocked replay; `notice` for the ordinary one. */
    readonly level: "notice" | "warning";
    readonly message: string;
}

export function report(pending: readonly Changeset[]): Report {
    const real = pending.filter((c) => !isEmpty(c)).length;
    const empty = pending.length - real;

    // The workflow never reaches this step with nothing pending — `has-changesets` would be false
    // and the publish path would have run instead. Someone reading the state by hand does, though,
    // and "0 pending, all empty" is not a description of an empty directory.
    if (pending.length === 0) {
        return {
            real,
            empty,
            level: "notice",
            message:
                "No changesets pending. The next push to develop goes straight to the publish " +
                "path, which releases whatever family is ahead of its tag — usually none.",
        };
    }

    if (real > 0) {
        return {
            real,
            empty,
            level: "notice",
            message:
                `${real} changeset(s) pending. The "Version Packages" pull request is this ` +
                `release — merging it is what publishes.`,
        };
    }
    return {
        real,
        empty,
        level: "warning",
        message:
            `${empty} pending changeset(s), all empty. No "Version Packages" pull request is ` +
            `opened and nothing can publish until a real one lands — a replay of a half-finished ` +
            `release included. Delete them if they are in the way; otherwise the next release ` +
            `consumes them.`,
    };
}

if (import.meta.main) {
    const { level, message } = report(changesets());
    console.log(`::${level}::${message}`);
}
