# Releasing

A release publishes all 46 libraries under one version to GitHub Packages, tags it, and cuts a
GitHub release. Almost all of it is automatic; the two human decisions are *what a change is worth*
and *when to cut*.

## The circuit

```
a PR touching libs/          →  pnpm changeset           the author says patch/minor/major, and why
merged into develop          →  Release workflow         opens or updates "Version Packages"
"Version Packages" merged    →  Release workflow         propagates, publishes, tags, releases
```

### 1. Every PR that touches `libs/` declares itself

```bash
pnpm changeset
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
  `scripts/sync-version.mjs`.

Those last two are the whole reason that script exists. The toolchain cannot be told a version from
the command line — no `-P`, no environment variable, no `${...}` in a `module.yaml` — so the version
is a literal line in the template all 46 libraries share. And `libs.versions.toml` carries it again
as `stx = "…"`, because the examples and the servers resolve *published coordinates*, not module
paths. Move one without the other and every example resolves something nobody published.

The script asserts each anchor matches exactly once and throws otherwise. If you reshape either
file and the release fails with *"found 0"*, that is it telling you the truth.

The PR updates itself as more changesets land. Leaving it open is how you batch a release.

### 3. Merging it releases

The same workflow re-runs with no changesets left and, in **one job**:

1. appends `publishing-github.repository.yaml` to the template's `repositories:` list and writes
   `creds.properties` from `GITHUB_TOKEN`;
2. `rm -rf build/incremental.state`;
3. `./kotlin publish github` with an explicit `-m` for every library;
4. tags `vX.Y.Z`, pushes it, and cuts a GitHub release from that version's CHANGELOG section.

## Five things that are not guessable

- **It is one job because a tag pushed with `GITHUB_TOKEN` triggers no workflow.** A separate
  `on: push: tags` publisher would simply never run. Publishing where the tag is made sidesteps it.
- **`rm -rf build/incremental.state` is load-bearing.** The publish task caches, and its
  up-to-date check does *not* notice a `module.yaml` edit — it reuses
  `build/tasks/_<module>_prepareMavenPublishables/`. The job has just rewritten `version:`, so
  without that line it republishes the previous version and reports success. Deleting artifacts
  from the repository does not help; the task will not regenerate them either.
- **A bare `./kotlin publish github` fails**, and not on a library: it walks every module in the
  project and stops at the first without that repository id — `Module 'demo-api' does not have
  repository with id 'github'`. An explicit `-m` selection is always passed. The three
  `jvm/amper-plugin` modules are not in it: the toolchain cannot publish a plugin yet.
- **The GitHub Packages block is not committed into the template.** `credentials.file` is resolved
  when the toolchain reads the *project model*, not when it publishes — committing it would make a
  token mandatory for `./kotlin show modules`, for everyone. Hence the fragment, and hence the rule
  that `repositories:` stays the last key of that file.
- **`stx-material`'s two Apple targets are silently skipped on Linux.** Releases are cut on
  `ubuntu-latest`, so `stx-material-iosarm64` and `-iossimulatorarm64` are published from a host
  that cannot build them. A green release says nothing about them. Its Compose resources are not in
  the publication either (KTC-5698).

## Releasing by hand

If the workflow is unavailable. You need a token with `write:packages`.

```bash
cat publishing-github.repository.yaml >> publishing.module-template.yaml
printf 'gpr.user=%s\ngpr.token=%s\n' "$USER" "$TOKEN" > creds.properties
rm -rf build/incremental.state
./kotlin publish github $(grep -rl publishing.module-template.yaml libs \
  --include=module.yaml | xargs -n1 dirname | xargs -n1 basename | sed 's/^/-m /')
git checkout publishing.module-template.yaml && rm -f creds.properties
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

## Maven Central

Not yet enabled, and everything it needs is already in place: the POM carries url, scm, licences and
a developer, and `publishSources: true` publishes a sources jar. What remains is a Central Portal
account, a PGP key, and:

```yaml
    mavenCentral: enabled
    signArtifacts: true
```

with `KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_USERNAME`, `..._PASSWORD` and `KOTLIN_TOOLCHAIN_SIGNING_KEY` in
the workflow. `io.github.softistx` is verified by owning the `softistx` GitHub organisation, so no
domain has to be proven and no coordinate changes. Publishing mode stays `manual` until the first
deployment has been looked at: Sonatype does not let artifacts be removed.
