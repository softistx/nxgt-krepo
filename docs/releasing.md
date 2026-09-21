# Releasing

A release publishes **the libraries that changed**, each under its own version, to **Maven Central**,
tags each one, and cuts a single GitHub release for the run. Almost all of it is automatic; the
three human decisions are *what a change is worth*, *when to cut*, and — because the Portal runs in
`manual` mode — *whether to actually release what was uploaded*.

## Seventeen release lines, not one

The 45 artifacts are grouped into **17 families**, one per directory under `libs/<role>/`. A family
is a library and its framework integrations: `stx-jpa`, `stx-jpa-ktor` and `stx-jpa-spring` are one
release line and carry one version, because they are one thing, released together, or not at all.

Each family owns four files:

| | |
| --- | --- |
| `package.json` | the version Changesets owns, and the family's runtime dependencies on other families |
| `<family>.module-template.yaml` | six lines: `apply: //publishing.module-template.yaml` and `settings.publishing.version`, which every `module.yaml` of the family applies |
| `CHANGELOG.md` | that family's history, from 0.2.1 on |
| `node_modules/` | bun's workspace symlinks, ignored |

`scripts/sync-version.ts` copies each `package.json` version into the two places the Kotlin build
reads it — the family template and the family's key in `[versions]` of `libs.versions.toml` — 34
anchors in all, each asserted to match exactly once.

**What the split buys, honestly.** A change to `stx-material`, `stx-jpa` or `stx-kafka` — which is
most changes — now moves one version line instead of seventeen. A change to `stx-common` still moves
fourteen, because the POMs of those fourteen really do name `stx-common:<version>`. That is not a
shortcoming of the setup; it is what depending on something means.

## The circuit

```
a PR changing a library      →  bun changeset            the author names the family, and patch/minor/major
merged into develop          →  Release workflow         opens or updates "Version Packages"
"Version Packages" merged    →  Release workflow         propagates, publishes what moved, tags, releases
```

### 1. Every PR that changes a library declares which family it releases

```bash
bun changeset
```

Pick the families, pick a bump, write one sentence a *reader* will see in the release notes, commit
the `.changeset/*.md` it writes. CI fails the PR without one, and fails it **naming the families you
changed and did not declare** — a changeset for the wrong family is worse than none, because it
releases a library nobody touched and leaves the touched one behind.

`bun changeset --empty` is the explicit "nothing published changes", and it satisfies the guard.
Use it for a test-only or comment-only change under `libs/`.

Changesets bumps the dependents itself, through the `dependencies` in each family's
`package.json` — which name only **runtime** edges. `compile-only` (POM scope `provided`) and
`test-dependencies` (absent from the POM) are deliberately not declared: they cannot reach a
consumer, so they must not move a version.

That list is written by hand, so `bun scripts/graph.ts --check` derives it again from the
`module.yaml` files the POMs are generated from and fails on any disagreement. It is worth a CI job
of its own because neither failure shows up anywhere else: a **missing** edge builds, tests and
publishes perfectly well, and merely stops releasing a library whose POM names a version that
moved; an **extra** one releases a library that nothing obliged to move. `bun scripts/graph.ts`
with no argument prints the graph.

A **`major` has to name its own dependents**, and the guard fails the pull request until it does,
printing the lines to paste. Changesets gives a dependent only a `patch` however far the dependency
moved, and the POM pins an exact version, so a consumer would take the break on a patch. An
`unaffected: <names>` line in the changeset's prose is the escape hatch, for a break that genuinely
cannot reach that dependent's own consumers. `.changeset/README.md` has the shape of both;
`docs/consuming.md` has the consumer's half.

### 2. Merging to `develop` opens a "Version Packages" PR

`changesets/action` collects every pending changeset, computes a bump **per family**, propagates to
the dependents, and opens a PR carrying:

- one `CHANGELOG.md` section per family that moved;
- `libs/*/*/package.json`, the versions Changesets owns;
- **the family templates and `libs.versions.toml`**, written by `scripts/sync-version.ts`.

Those last are the whole reason that script exists. The toolchain cannot be told a version from the
command line — no `-P`, no environment variable, no `${...}` in a `module.yaml` — so a version is a
literal line in a file. And `libs.versions.toml` carries each one again, because the examples and
the servers resolve *published coordinates*, not module paths. Move one without the other and an
example resolves something nobody published.

The script asserts each anchor matches exactly once and throws otherwise. If you reshape either file
and the release fails with *"found 0"*, that is it telling you the truth. It also checks the
*structure*: every `module.yaml` under `libs/` applies exactly one family template, every family
template chains to the shared one, and every family has a `package.json`. A module wired to no
family template builds fine and fails only at `kotlin publish` — `bun scripts/sync-version.ts
--check` in CI is that failure, moved to the pull request.

The PR updates itself as more changesets land. Leaving it open is how you batch a release.

### 3. Merging it releases what moved

The same workflow re-runs with no changesets left and, in **one job**:

1. `bun x changeset publish-plan` — the families whose version is ahead of their
   `<family>@<version>` git tag, in dependency order, in batches. That is the whole selection: no
   script here decides what to publish. `bun scripts/release-plan.ts` only translates family names
   into the module names the toolchain publishes;
2. **an empty plan ends the run** with a notice. That is the expected outcome of most pushes to
   `develop`, and it replaces the old `git rev-parse "v$VERSION"` guard;
3. `bun scripts/sync-version.ts --check` — fails in a second if a family template and its
   `package.json` have drifted, before anything reaches a repository that does not allow a version
   to be replaced;
4. checks the three Central secrets are non-empty and fails in seconds if not;
5. runs `scripts/enable-central.ts`, which turns on `mavenCentral` and `signArtifacts`;
6. `rm -rf build/incremental.state`;
7. `./kotlin task :<module>:publishToMavenCentral` for every module of every selected family — not
   `kotlin publish`, see below;
8. **then** `bun x changeset git-tag`, which creates `<family>@<version>` for each and pushes them;
9. one GitHub release on a `release-<YYYY-MM-DD>` tag, its body one section per family, taken from
   that family's own `CHANGELOG.md`.

**The tags come after the upload, not before**, so a failed publish cannot leave a tag claiming a
version went out. `changeset git-tag` skips tags that already exist, which is what lets a run whose
*plan* was partly consumed be replayed: the families already tagged are no longer ahead of their
tag, so they are not in the next plan, and Central is never asked to take the same version twice.

What that does **not** cover is a failure inside the publish step itself. The 45 uploads run as one
`./kotlin task` invocation, so if the twentieth fails, nineteen bundles are in the Portal and
nothing is tagged — and a re-run would offer Central versions it already holds, which it refuses.
The atomic unit of "already uploaded" is the artifact, not the family, so no tagging scheme fixes
this; the escape hatch is that **nothing is released until a human releases it**. Drop the
deployments the failed run left in
[the deployments list](https://central.sonatype.com/publishing/deployments), then re-run the job
from the Actions tab. It is the same drop-and-retry as below, done before anything was public.

**One release for the run, not one per family.** The family tags stay bare. Seventeen release
entries for one merge would make `/releases` unreadable, and the notes are the same notes either
way.

### 4. You release it from the Portal

`publishingMode` is `manual`, so the job uploads a bundle, waits for Sonatype to validate it, and
stops. Nothing is public until someone opens
[central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments)
and releases it. That is deliberate: **Sonatype does not let artifacts be removed from Central.**
Switching to `auto` is a one-word change in `scripts/enable-central.ts`, and its test asserts
`manual` so that the change has to be intentional.

## Six things that are not guessable

- **It is one job because a tag pushed with `GITHUB_TOKEN` triggers no workflow.** A separate
  `on: push: tags` publisher would simply never run. Publishing where the tag is made sidesteps it.
- **`rm -rf build/incremental.state` is load-bearing.** The publish task caches, and its
  up-to-date check does *not* notice a manifest edit — it reuses
  `build/tasks/_<module>_prepareMavenPublishables/`. The job has just rewritten up to seventeen
  family templates *and* enabled signing, so without that line it republishes the previous version, unsigned, and reports
  success. Deleting artifacts from the repository does not help; the task will not regenerate them.
- **`kotlin publish mavenCentral` does not work on toolchain 0.12.0**, so the job runs the Portal
  upload task directly. The command refuses — *"Cannot publish to repository 'mavenCentral' because
  it's not marked as publishable"* — because it checks the repositories list, where the built-in
  `mavenCentral` is resolve-only; an entry giving it `publish: true` registers a second
  `publishToMavenCentral` task and crashes the CLI with *"Task … already exists"*.
  `settings.publishing.mavenCentral` still registers that task per module, and `./kotlin task` —
  not in `--help` — runs it. The three `jvm/amper-plugin` modules are not in the list: the
  toolchain cannot publish a plugin yet.
- **Maven Central and signing are not committed enabled**, and there is no third option. Both were
  measured:

  | committed | `./kotlin build` / `test` | `./kotlin publish mavenLocal`, no PGP key |
  | --- | --- | --- |
  | `mavenCentral` + `signArtifacts: true` | fine | **fails** — *"Artifact signing is enabled, but the KOTLIN_TOOLCHAIN_SIGNING_KEY environment variable is not provided"* |
  | `mavenCentral` + `signArtifacts: false` | **fails at model load** — *"Maven Central requires artifact signatures, but signing is disabled"* | fails |
  | neither | fine | fine |

  The middle row breaks everyone, and the top row breaks the mavenLocal publish that every
  contributor does on every change under `libs/`. So the template ships with neither and
  `scripts/enable-central.ts` adds both in the release job, with a test pinning where it inserts
  and that it refuses when the template no longer matches.
- **A tag says *uploaded*, not *released*.** The Portal is in `manual` mode, so the job tags a
  family once its bundle is up and validated — long before anyone decides what to do with it. If you
  then *drop* a deployment in the Portal, the tag lies, and the next run will skip that family
  because it is no longer ahead of it. Delete it, and the family comes back into the plan:

  ```bash
  git push --delete origin stx-jpa@0.5.2 && git tag -d stx-jpa@0.5.2
  ```

  The hole existed before, with a single `vX.Y.Z`; it now exists seventeen times over.
- **`stx-material`'s two Apple targets are silently skipped on Linux.** Releases are cut on
  `ubuntu-latest`, so `stx-material-iosarm64` and `-iossimulatorarm64` go out from a host that
  cannot build them. A green release says nothing about them. Its Compose resources are not in the
  publication either (KTC-5698).

## Secrets

Three, as organisation secrets on `softistx`:

| | |
| --- | --- |
| `KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_USERNAME` | the username half of a Central Portal user token |
| `KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_PASSWORD` | the password half |
| `KOTLIN_TOOLCHAIN_SIGNING_KEY` | the PGP private key, ASCII-armored — `gpg --export-secret-keys --armor <KEY_ID>` |

`KOTLIN_TOOLCHAIN_SIGNING_KEY_PASSPHRASE` is optional and unset: the key has no passphrase.

**An organisation secret whose repository access does not include this repository arrives as an
empty string, not as an error.** The job therefore checks all three are non-empty before doing
anything, so that misconfiguration costs seconds rather than a 401 at the end of a long build. On
the Free plan the same goes for any *private* repository: organisation secrets are not passed to it
at all. That is how the first 0.2.0 run failed, before this repository went public.

## Releasing by hand

If the workflow is unavailable. You need the Central Portal token and the PGP key in your
environment.

```bash
export KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_USERNAME=... KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_PASSWORD=...
export KOTLIN_TOOLCHAIN_SIGNING_KEY="$(gpg --export-secret-keys --armor <KEY_ID>)"

bun x changeset publish-plan --output plan.json
bun scripts/release-plan.ts plan.json          # read it before doing anything

bun scripts/sync-version.ts --check
bun scripts/enable-central.ts
rm -rf build/incremental.state
./kotlin task $(bun scripts/release-plan.ts plan.json --tasks)
git checkout publishing.module-template.yaml

bun x changeset git-tag && git push origin --tags
```

Then release the deployment from the Portal. To publish *everything* regardless of what moved —
which you want only when rebuilding a line from scratch — `bun scripts/modules.ts --tasks` names all
45.

To check what you are about to publish *without* signing or a token, publish to mavenLocal instead
and read the result:

```bash
./kotlin publish mavenLocal $(bun scripts/modules.ts --publish-args)
```

Then check the result rather than the exit code — a dependency published with no version is the
failure this catches:

```bash
python3 -c 'import json,glob,os
for p in glob.glob(os.path.expanduser("~/.m2/repository/io/github/softistx/*/*/*.module")):
    d = json.load(open(p))
    for v in d.get("variants", []):
        for x in v.get("dependencies", []):
            if not x.get("version"):
                print("NO VERSION:", os.path.basename(p), v["name"], x["module"])'
```

That happens when a library names a `- bom:` entry: a BOM does not survive publication. It lands in
the Gradle metadata as an ordinary dependency with no `dependencyConstraints`, so everything it was
managing publishes with no version at all and a consumer fails with *"its version could not be
resolved"*. Give the catalog alias a `version.ref` and drop the `bom:` line.

## The bootstrap, and why it mattered once

Selection is "the families ahead of their tag". Immediately after the split **no `<family>@<version>`
tag existed**, so all 17 were ahead of nothing and the first run would have tried to upload 0.2.1
over the 0.2.1 already on Central — which refuses a replaced version, after 45 bundles of rubbish
had landed in the deployments list. The seventeen `stx-*@0.2.1` tags were therefore pushed by hand
before the split reached `develop`:

```bash
bun x changeset git-tag                    # 17 tags at the current version, on HEAD
for t in $(git tag -l 'stx-*@0.2.1'); do   # move them to where 0.2.1 was released
  git tag -d "$t" && git tag "$t" v0.2.1^{commit}
done
git push origin --tags
```

The second step matters for reading, not for working: `publish-plan` only asks whether a tag
*exists*, but a tag saying `stx-jpa@0.2.1` should point at the commit that released 0.2.1, not at
whatever was checked out when the command ran. `changeset git-tag` has no way to be told otherwise.
With all 17 in place the plan comes back empty, which is the assertion the migration rests on: the
first run under this regime publishes nothing.

It is written down because the same thing happens to any family added later: give it a tag at the
version it starts from, or its first release tries to publish a version that is already out. A
family created at `0.0.0` and first released at `0.1.0` needs none — it is only a family starting at
a version already on Central that does.

## And `main`

Nothing. A release happens entirely on `develop`. `main` is aligned when someone asks, with
`git checkout main && git merge develop`, so it trails the latest release — which is consistent
with the branch rule in AGENTS.md, where `main` is never a pull request target. Releasing from
`main` would need a "Version Packages" PR opened against it, which this repository does not allow.

## Why Maven Central and not GitHub Packages

GitHub's Maven registry requires a token even to *read* a public package — there is no anonymous
read. For a repository whose point is that other people use the libraries, that is a tax on every
consumer. Central has none, and `io.github.softistx` is verified by owning the `softistx` GitHub
organisation, so no domain had to be proven and no coordinate changed to get there.
