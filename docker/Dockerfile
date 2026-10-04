# syntax=docker/dockerfile:1
#
# The repository's images, built with its root as the context. `docs/examples-in-docker.md` has the
# whole arrangement; the two targets are:
#
#   workspace  the dev container: a JDK, the toolchain, git and bun, nothing of the repository.
#              Compose mounts the checkout at /workspace and the IDE attaches to it
#              (.devcontainer/devcontainer.json); any module runs from there with `./kotlin run`.
#   prod       one example's executable jar on a JRE, built from a copy of the checkout.

ARG JDK_IMAGE=eclipse-temurin:25-jdk
ARG JRE_IMAGE=eclipse-temurin:25-jre

# ── the toolchain, shared by both ─────────────────────────────────────────────────────────────────
# The `./kotlin` wrapper downloads its distribution with curl or wget, and the Temurin image has
# neither — measured: "Please install 'wget' or 'curl'". JDK 25 is the toolchain's default
# requirement, so with JAVA_HOME already pointing at one it provisions no second JDK, and
# KOTLIN_CLI_JAVA_HOME has the CLI itself run on it rather than download its own JRE.
FROM ${JDK_IMAGE} AS toolchain
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
ENV KOTLIN_CLI_JAVA_HOME=/opt/java/openjdk \
    KOTLIN_CLI_NO_WELCOME_BANNER=1
WORKDIR /workspace

# ── workspace ─────────────────────────────────────────────────────────────────────────────────────
# What working in the repository needs beyond the toolchain: git, and bun for `scripts/` and
# changesets — pinned to the version the host runs, since bun strips types rather than checking them
# and two versions can disagree. bun's installer unpacks a zip.
FROM toolchain AS workspace
ARG BUN_VERSION=1.4.2
RUN apt-get update \
 && apt-get install -y --no-install-recommends git unzip \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL https://bun.sh/install | bash -s "bun-v${BUN_VERSION}" \
 && git config --system --add safe.directory /workspace
# The checkout is the host's, owned by another uid, and git refuses a repository it does not own
# until told otherwise.
ENV PATH=/root/.bun/bin:$PATH
# The IDE runs its backend in here and the container's own work is done through it, so the
# container's process is only something to keep it alive.
CMD ["sleep", "infinity"]

# ── prod ──────────────────────────────────────────────────────────────────────────────────────────
FROM toolchain AS build
ARG MODULE
COPY . .
# The examples resolve `io.github.softistx:*` from mavenLocal on purpose (stx-artifacts template),
# so the host's ~/.m2/repository is lent to this step as the `m2` build context — read-only, and
# never copied into a layer.
#
# The jar's path is the toolchain's task layout, read off a real build:
# tasks/_<module>_executableJarJvm/<module>-jvm-executable.jar. The build directory is a cache per
# module, so two examples building at once never share one; the toolchain cache is shared, locked.
RUN --mount=type=cache,target=/root/.cache,sharing=locked \
    --mount=type=cache,id=krepo-build-${MODULE},target=/build \
    --mount=type=bind,from=m2,target=/root/.m2/repository \
    ./kotlin package -m "$MODULE" -f executable-jar --build-dir /build \
 && cp "/build/tasks/_${MODULE}_executableJarJvm/${MODULE}-jvm-executable.jar" /app.jar

FROM ${JRE_IMAGE} AS prod
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
# A container's memory, not the host's: the JVM's default heap is a quarter of what it can see. In
# the image rather than in compose, so the workspace image — another target — never inherits it.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
