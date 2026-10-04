#!/bin/sh
# Gives an example its own role and database on the workspace's running Postgres, as its superuser.
# Run by the example's `-db-init` service in the repository root's compose.yaml, before the
# application starts.
#
# Idempotent: the role and the database are created when missing, and the role's password is set
# every time, so changing APP_DB_PASSWORD in .env takes effect on the next `up`. Nothing is ever
# dropped. The names and the password reach SQL as psql variables, quoted by %I / %L, never spliced
# into the statement by the shell.
set -eu

for v in PGHOST PGUSER PGPASSWORD APP_DB_NAME APP_DB_USER APP_DB_PASSWORD; do
    eval "val=\${$v:-}"
    [ -n "$val" ] || { echo "postgres-init: $v is not set — see .env.example at the repository root" >&2; exit 1; }
    # `${X:?}` in compose refuses only an empty value; the template's placeholder would otherwise
    # become a real password.
    [ "$val" != CHANGE_ME ] || { echo "postgres-init: $v is still the template's CHANGE_ME" >&2; exit 1; }
done

tries=0
until pg_isready -q -d postgres; do
    tries=$((tries + 1))
    [ "$tries" -lt 30 ] || { echo "postgres-init: $PGHOST did not answer in 30 s" >&2; exit 1; }
    sleep 1
done

psql -v ON_ERROR_STOP=1 -q -d postgres \
    -v app_db="$APP_DB_NAME" -v app_user="$APP_DB_USER" -v app_password="$APP_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'app_user')
 WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app_user') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'app_db', :'app_user')
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'app_db') \gexec
SQL

echo "postgres-init: role $APP_DB_USER owns database $APP_DB_NAME on $PGHOST"
