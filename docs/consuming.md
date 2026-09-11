# Using these libraries

The libraries publish as `io.github.softistx:<module-name>:<version>` — the directory name under
`libs/` *is* the artifact name, so `libs/stx-jpa/stx-jpa` is `io.github.softistx:stx-jpa`. They are
ordinary Maven artifacts with Gradle module metadata beside the POM: **you do not need the Kotlin
Toolchain to consume them.** Gradle, Maven, and anything else that reads a Maven repository work.

The current version is the latest [release](https://github.com/softistx/nxgt-krepo/releases); the
`CHANGELOG.md` at the repository root carries the same history once the first one is cut. All 46
artifacts carry the same version — see
[Why one version](#why-they-all-carry-the-same-version).

## Where they live

Today: **GitHub Packages**, at `https://maven.pkg.github.com/softistx/nxgt-krepo`.

> **GitHub Packages requires a token even to read a public package.** This is a GitHub limitation,
> not a choice made here — the Maven registry has no anonymous read. You need a classic personal
> access token with the single scope `read:packages`:
> [github.com/settings/tokens](https://github.com/settings/tokens/new?scopes=read:packages).
> Keep it out of source control; every snippet below reads it from somewhere else.
>
> Maven Central is the destination — it needs no token — and the artifacts already carry the POM
> metadata it requires. Until then, the token is the cost of using these.

## Gradle (Kotlin DSL)

`settings.gradle.kts`, or `build.gradle.kts` if you declare repositories there:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/softistx/nxgt-krepo")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

with the two properties in `~/.gradle/gradle.properties` — **your home directory, not the project**:

```properties
gpr.user=your-github-username
gpr.token=ghp_yourtokenhere
```

Then:

```kotlin
dependencies {
    implementation("io.github.softistx:stx-jpa:0.1.0")
    implementation("io.github.softistx:stx-jpa-ktor:0.1.0")
}
```

## Maven

`pom.xml`:

```xml
<repositories>
  <repository>
    <id>softistx</id>
    <url>https://maven.pkg.github.com/softistx/nxgt-krepo</url>
  </repository>
</repositories>

<dependency>
  <groupId>io.github.softistx</groupId>
  <artifactId>stx-jpa</artifactId>
  <version>0.1.0</version>
</dependency>
```

and the credentials in `~/.m2/settings.xml`, matching that `<id>`:

```xml
<servers>
  <server>
    <id>softistx</id>
    <username>your-github-username</username>
    <password>ghp_yourtokenhere</password>
  </server>
</servers>
```

## Kotlin Toolchain

`module.yaml`:

```yaml
product: jvm/app

repositories:
  - id: softistx
    url: https://maven.pkg.github.com/softistx/nxgt-krepo
    credentials:
      file: creds.properties
      usernameKey: gpr.user
      passwordKey: gpr.token

dependencies:
  - io.github.softistx:stx-jpa:0.1.0
```

Two things the toolchain does differently, and both bite if you assume otherwise:

- **Credentials come from a `.properties` file and nothing else.** No environment variable, no
  `~/.m2/settings.xml`, no `${...}` interpolation in a manifest. `file:` is a path relative to *the
  file that declares it* — so in the snippet above, relative to that `module.yaml`. Gitignore it.
- **It is read when the project model is loaded, not when a dependency is fetched.** A missing
  `creds.properties` fails `./kotlin show modules`, not just the build. If that is awkward for your
  contributors, keep the block in a template your CI applies rather than in every manifest.

## Which artifact you actually want

A library and its framework integration are separate artifacts, on purpose — so that depending on
`stx-jpa` does not drag Ktor or Spring into a project that uses neither:

| you are building | you want |
| --- | --- |
| a plain JVM library or app | `stx-jpa`, `stx-mongo`, `stx-redis`, … |
| a Ktor server | that, plus `stx-jpa-ktor`, `stx-mongo-ktor`, … |
| a Spring Boot service | that, plus `stx-jpa-spring`, `stx-mongo-spring`, … — and `stx-spring-boot` |
| tests against a real backing service | `stx-testing` |

Each library's README says what it is for; the `docs/` pages say what it can be told to do.

## Why they all carry the same version

`io.github.softistx:stx-common` and `io.github.softistx:stx-workflow-db` are always the same
version, and a release moves all 46 at once. That is not laziness:

- the toolchain has no way to override a publication version per module — it is one literal line in
  a template every library shares;
- publishing is all-or-nothing across a dependency chain, so a change to `stx-common` republishes
  everything that depends on it regardless;
- mixing versions across the set is therefore never something we have tested, and the Gradle
  metadata will not stop you.

Take them at one version. `CHANGELOG.md` names which libraries actually changed in each release.

## Sources, and what is missing

Every artifact publishes a sources jar, so your IDE shows the code and its comments rather than a
decompiled signature. The javadoc jar is empty — the toolchain adds one automatically and does not
yet offer control over it.

`stx-material` is the one multiplatform library: it publishes a root artifact plus one per platform
(`stx-material-jvm`, `-android`, `-iosarm64`, `-iossimulatorarm64`). Its Compose resources are
**not** in the publication yet
([KTC-5698](https://youtrack.jetbrains.com/issue/KTC-5698/Support-publication-of-composeResources-as-a-part-of-KMP-library-publication)) —
the jar publishes, the resources do not. The two Apple targets are built only on an Apple host, and
releases are cut on Linux, so treat them as untested until that changes.
