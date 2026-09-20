# Releasing

A release publishes all 45 libraries under one version to **Maven Central**, tags it, and cuts a
GitHub release. Almost all of it is automatic; the three human decisions are *what a change is
worth*, *when to cut*, and — because the Portal runs in `manual` mode — *whether to actually
release what was uploaded*.

## The circuit

```
a PR touching libs/          →  bun changeset           the author says patch/minor/major, and why
merged into develop          →  Release workflow         opens or updates "Version Packages"
"Version Packages" merged    →  Release workflow         propagates, publishes, tags, releases
```

### 1. Every PR that touches `libs/` declares itself

```bash
bun changeset
```

Pick a bump, write one sentence a *reader* will see in the release notes, commit the
`.changeset/*.md` it writes. CI fails the PR without one — that job exists because this is the only
step nobody can automate.

There is one package and it is the repository, so a `minor` is a minor for the whole line. Name the
library in the prose instead: `stx-graphix: exception handlers that reach the client`.

`.changeset/README.md` has the reasoning; `docs/consuming.md` has the consumer's half of it.

### 2. Merging to `develop` opens a "Version Packages" PR

`changesets/action` collects every pending changeset, computes one bump, and opens a PR carrying:

- `CHANGELOG.md`, one section per release, grouped major/minor/patch;
- `package.json`, the version Changesets owns;
- **`publishing.module-template.yaml` and `libs.versions.toml`**, written by
  `scripts/sync-version.ts`.

Those last two are the whole reason that script exists. The toolchain cannot be told a version from
the command line — no `-P`, no environment variable, no `${...}` in a `module.yaml` — so the version
is a literal line in the template all 45 libraries share. And `libs.versions.toml` carries it again
as `stx = "…"`, because the examples and the servers resolve *published coordinates*, not module
paths. Move one without the other and every example resolves something nobody published.

The script asserts each anchor matches exactly once and throws otherwise. If you reshape either
file and the release fails with *"found 0"*, that is it telling you the truth.

The PR updates itself as more changesets land. Leaving it open is how you batch a release.

### 3. Merging it releases

The same workflow re-runs with no changesets left and, in **one job**:

1. checks the three Central secrets are non-empty and fails in seconds if not;
2. runs `scripts/enable-central.ts`, which turns on `mavenCentral` and `signArtifacts`;
3. `rm -rf build/incremental.state`;
4. `./kotlin task :<library>:publishToMavenCentral` for every library `scripts/modules.ts` names —
   not `kotlin publish`, see below;
5. tags `vX.Y.Z`, pushes it, and cuts a GitHub release from that version's CHANGELOG section.

### 4. You release it from the Portal

`publishingMode` is `manual`, so the job uploads a bundle, waits for Sonatype to validate it, and
stops. Nothing is public until someone opens
[central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments)
and releases it. That is deliberate: **Sonatype does not let artifacts be removed from Central.**
Switching to `auto` is a one-word change in `scripts/enable-central.ts`, and its test asserts
`manual` so that the change has to be intentional.

## Five things that are not guessable

- **It is one job because a tag pushed with `GITHUB_TOKEN` triggers no workflow.** A separate
  `on: push: tags` publisher would simply never run. Publishing where the tag is made sidesteps it.
- **`rm -rf build/incremental.state` is load-bearing.** The publish task caches, and its
  up-to-date check does *not* notice a `module.yaml` edit — it reuses
  `build/tasks/_<module>_prepareMavenPublishables/`. The job has just rewritten `version:` *and*
  enabled signing, so without that line it republishes the previous version, unsigned, and reports
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

bun scripts/enable-central.ts
rm -rf build/incremental.state
./kotlin task $(bun scripts/modules.ts --tasks)
git checkout publishing.module-template.yaml
```

Then release the deployment from the Portal.

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
