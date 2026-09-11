# Using these libraries

The libraries publish to **Maven Central** as `io.github.softistx:<module-name>:<version>` — the
directory name under `libs/` *is* the artifact name, so `libs/stx-jpa/stx-jpa` is
`io.github.softistx:stx-jpa`. They are ordinary Maven artifacts with Gradle module metadata beside
the POM: **you do not need the Kotlin Toolchain to consume them**, and you need no credentials.

The current version is the latest [release](https://github.com/softistx/nxgt-krepo/releases).

## Gradle

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.softistx:stx-jpa:0.2.0")
    implementation("io.github.softistx:stx-jpa-ktor:0.2.0")
}
```

## Maven

```xml
<dependency>
  <groupId>io.github.softistx</groupId>
  <artifactId>stx-jpa</artifactId>
  <version>0.2.0</version>
</dependency>
```

## Kotlin Toolchain

```yaml
product: jvm/app

dependencies:
  - io.github.softistx:stx-jpa:0.2.0
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

## They all carry the same version

`io.github.softistx:stx-common` and `io.github.softistx:stx-workflow-db` are always the same
version, and a release moves all 45 at once. That is not laziness:

- the toolchain has no way to override a publication version per module — it is one literal line in
  a template every library shares;
- publishing is all-or-nothing across a dependency chain, so a change to `stx-common` republishes
  everything that depends on it regardless;
- mixing versions across the set is therefore never something that gets tested here, and the Gradle
  metadata will not stop you.

Take them at one version. The release notes name which libraries actually changed.

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
- the two Apple targets are built only on an Apple host, and releases are cut on Linux, where the
  toolchain **skips them silently**. Treat them as untested until that changes.

## Pre-1.0

These are `0.x`. A minor bump may break you — the group itself moved from `com.softistx` to
`io.github.softistx` before the first release. Breaking changes are called out at the top of each
release's notes, and the commit that makes one carries a `!` in its subject.
