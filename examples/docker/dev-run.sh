#!/bin/sh
# The `dev` container's command: compile the mounted checkout, then become the application.
#
# Not `./kotlin run`. That keeps the toolchain's own JVM alive as the application's parent for as
# long as the application runs — measured on jpa-shop: 450 MiB of a 790 MiB container, doing
# nothing once the port was open. Packaging and then `exec java` lets the toolchain exit, and the
# application is the only process left; the build directory is a volume, so the package is
# incremental across restarts.
set -eu

[ -n "${MODULE:-}" ] || { echo "dev-run: MODULE is not set; the compose.yaml names it" >&2; exit 1; }
jar="/build/tasks/_${MODULE}_executableJarJvm/${MODULE}-jvm-executable.jar"

./kotlin package -m "$MODULE" -f executable-jar --build-dir /build

# APP_JAVA_OPTS, not JAVA_TOOL_OPTIONS: the latter would reach the toolchain's JVM above as well.
# shellcheck disable=SC2086
exec java ${APP_JAVA_OPTS:-} -jar "$jar"
