#!/bin/sh
# Gives an example its own user on the workspace's running replica set, as its root user. Run by
# the `db-init` service of an example's compose.yaml, before the application starts.
#
# The user lives in `admin` and holds readWrite on each database of APP_DBS and nothing else — not
# the readWriteAnyDatabase the workspace's shared application user has. Idempotent: an existing user
# is brought to exactly these roles and this password. The values reach mongosh through
# process.env from a QUOTED heredoc, so the shell never splices a password into JavaScript.
set -eu

for v in MONGO_HOSTS MONGO_ADMIN_USER MONGO_ADMIN_PASSWORD APP_DB_USER APP_DB_PASSWORD APP_DBS; do
    eval "val=\${$v:-}"
    [ -n "$val" ] || { echo "mongo-init: $v is not set — see the example's .env.example" >&2; exit 1; }
    # `${X:?}` in compose refuses only an empty value; the template's placeholder would otherwise
    # become a real password.
    [ "$val" != CHANGE_ME ] || { echo "mongo-init: $v is still the template's CHANGE_ME" >&2; exit 1; }
done

admin() {
    mongosh --quiet --host "$MONGO_HOSTS" \
        -u "$MONGO_ADMIN_USER" -p "$MONGO_ADMIN_PASSWORD" --authenticationDatabase admin "$@"
}

tries=0
until admin --eval 'db.runCommand({ ping: 1 }).ok' >/dev/null 2>&1; do
    tries=$((tries + 1))
    [ "$tries" -lt 30 ] || { echo "mongo-init: could not log in to $MONGO_HOSTS as $MONGO_ADMIN_USER" >&2; exit 1; }
    sleep 1
done

admin --eval "$(cat <<'JS'
const user = process.env.APP_DB_USER;
const pwd = process.env.APP_DB_PASSWORD;
const roles = process.env.APP_DBS.split(",").map(db => ({ role: "readWrite", db: db.trim() }));
const admin = db.getSiblingDB("admin");
if (admin.getUser(user)) admin.updateUser(user, { pwd, roles });
else admin.createUser({ user, pwd, roles });
print(`mongo-init: ${user} has readWrite on ${process.env.APP_DBS}`);
JS
)"
