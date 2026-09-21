# Using these libraries

The libraries publish to **Maven Central** as `io.github.softistx:<module-name>:<version>` — the
directory name under `libs/` *is* the artifact name, so `libs/data/stx-jpa/stx-jpa` is
`io.github.softistx:stx-jpa`. They are ordinary Maven artifacts with Gradle module metadata beside
the POM: **you do not need the Kotlin Toolchain to consume them**, and you need no credentials.

**Each library family carries its own version** — the snippets below use `0.2.1`, the version
everything shared before the split. For a given artifact's current version, look at its
`<family>@<version>` [tag](https://github.com/softistx/nxgt-krepo/tags) or at Maven Central.
[*Versions move per family*](#versions-move-per-family-not-all-at-once) below says what that
does and does not promise.

## Gradle

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.softistx:stx-jpa:0.2.1")
    implementation("io.github.softistx:stx-jpa-ktor:0.2.1")
}
```

## Maven

```xml
<dependency>
  <groupId>io.github.softistx</groupId>
  <artifactId>stx-jpa</artifactId>
  <version>0.2.1</version>
</dependency>
```

## Kotlin Toolchain

```yaml
product: jvm/app

dependencies:
  - io.github.softistx:stx-jpa:0.2.1
```

Maven Central is a default repository, so there is no `repositories:` block to write.

## Which artifact you actually want

A library and its framework integration are separate artifacts, on purpose — so that depending on
`stx-jpa` drags in neither Ktor nor Spring:

| you are building | you want |
| --- | --- |
| a plain JVM library or app | `stx-jpa`, `stx-mongo`, `stx-redis`, `stx-kafka`, `stx-amqp`, `stx-storage`, `stx-graphix`, `stx-workflow`, `stx-migrations`, `stx-telemetry`, `stx-i18n` |
| a Ktor server | that, plus the matching `*-ktor` — and `stx-ktor` for the lifecycle idiom they share |
| a Spring Boot service | that, plus the matching `*-spring` — and `stx-spring-boot` |
| a Compose Multiplatform UI | `stx-material` |
| specs against a real backing service | `stx-testing` |

Each library's README says what it is for; the pages under [`docs/`](.) say what it can be told to
do — [`jpa-criteria.md`](jpa-criteria.md) for what a query may say, [`graphix.md`](graphix.md) for
what a schema may say, [`spring-configuration.md`](spring-configuration.md) for every `stx.*` key,
and so on.

## Versions move per family, not all at once

A **family** is a library and its framework integrations. `io.github.softistx:stx-jpa`,
`stx-jpa-ktor` and `stx-jpa-spring` are one release line: they always carry the same version, they
are released together, and mixing versions *within* a family is not something that is tested here.
Take a family at one version.

Across families, versions diverge on purpose. There are 17 of them, and each moves when its own
code changes — or when a family it depends on **at runtime** changes, because the POM names that
dependency at an exact version, so a new `stx-common` really does mean a new `stx-jpa`. What this
buys you is the ability to read a version bump: `stx-material 0.4.0` after `0.3.1` means something
about `stx-material` changed. It no longer means someone fixed a typo in the Kafka client.

Two consequences worth knowing:

- **a dependency bump looks like a patch, and may not be one.** Changesets gives a dependent a
  `patch` whatever the dependency did, so a family that moved only because `stx-common` went `major`
  arrives as a patch with a breaking transitive dependency behind it. The release notes say so — the
  family's section names the dependency that moved. Read them before taking a bump you did not ask
  for;
- **everything up to and including 0.2.1 was released in lockstep.** All 45 artifacts share that
  history, which is in the [root `CHANGELOG.md`](../CHANGELOG.md). From 0.2.1 on, each family has
  its own, next to its sources under `libs/<role>/<family>/CHANGELOG.md`.

Every artifact of every family still comes from one build, so the set as a whole is consistent at
any commit. What is no longer promised is that two coordinates you pick at random carry the same
number.

## Sources, signatures, and what is missing

Every artifact publishes a **sources jar**, so your IDE shows the code and the comments explaining
why it is shaped that way rather than a decompiled signature. Every artifact is **PGP-signed**, as
Maven Central requires. The javadoc jar is empty — the toolchain adds one automatically and does not
yet offer control over it.

`stx-material` is the one multiplatform library. It publishes a root artifact plus one per platform
(`stx-material-jvm`, `-android`, `-iosarm64`, `-iossimulatorarm64`). Two things to know:

- its Compose resources are **not** in the publication yet
  ([KTC-5698](https://youtrack.jetbrains.com/issue/KTC-5698/Support-publication-of-composeResources-as-a-part-of-KMP-library-publication)) —
  the jar publishes, the resources do not;
- the two Apple targets **are** compiled, including on the Linux host releases are cut from —
  `./kotlin publish` cross-compiles them even though `./kotlin build` does not, and the published
  klibs are real. What is not covered is linking: a klib is compiled, never linked into a framework
  by anything here, and there are no iOS tests. Treat them as compiled but unexercised.

## Pre-1.0

These are `0.x`. A minor bump may break you — the group itself moved from `com.softistx` to
`io.github.softistx` before the first release. Breaking changes are called out at the top of each
release's notes, and the commit that makes one carries a `!` in its subject.
