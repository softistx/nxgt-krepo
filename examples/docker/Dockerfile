# syntax=docker/dockerfile:1
#
# One Dockerfile for every runnable example, built with the repository root as its context.
# `docs/examples-in-docker.md` has the whole arrangement; the two targets are:
#
#   dev   the toolchain and a JDK, nothing of the repository. Compose mounts the checkout at
#         /workspace and dev-run.sh compiles it, then runs the jar, so an edit is one
#         `docker compose restart` away.
#   prod  the module's executable jar on a JRE, built from a copy of the checkout.

ARG JDK_IMAGE=eclipse-temurin:25-jdk
ARG JRE_IMAGE=eclipse-temurin:25-jre

# ── dev ───────────────────────────────────────────────────────────────────────────────────────────
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

FROM toolchain AS dev
# dev-run.sh packages into /build — outside the mount: the host's own `build/` belongs to the IDE
# and to `./kotlin` on macOS, and a Linux build writing into it would invalidate both — then execs
# the jar, so the toolchain does not outlive the compile.
COPY examples/docker/dev-run.sh /usr/local/bin/dev-run
ENTRYPOINT ["dev-run"]

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
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
