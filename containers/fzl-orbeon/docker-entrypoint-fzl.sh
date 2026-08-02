#!/usr/bin/env bash
# fzl-orbeon entrypoint:
#   1. wait for fzl-postgresql
#   2. create the 'orbeon' database + apply Orbeon's schema if missing (idempotent)
#   3. render Tomcat's JDBC datasource (context.xml) and Orbeon's
#      properties-local.xml from environment variables
#   4. hand off to Tomcat
#
# This makes the container self-provisioning: it owns its own database
# bootstrap, so a fresh clone / new machine needs no manual DB setup.
set -euo pipefail

: "${FZL_POSTGRES_USER:?FZL_POSTGRES_USER is required}"
: "${FZL_POSTGRES_PASSWORD:?FZL_POSTGRES_PASSWORD is required}"
: "${FZL_ORBEON_CRYPTO_PASSWORD:?FZL_ORBEON_CRYPTO_PASSWORD is required}"

DB_HOST="${FZL_ORBEON_DB_HOST:-fzl-postgresql}"
DB_PORT="${FZL_ORBEON_DB_PORT:-5432}"
DB_NAME="${FZL_ORBEON_DB_NAME:-orbeon}"

export PGPASSWORD="$FZL_POSTGRES_PASSWORD"
psql_admin() { psql -h "$DB_HOST" -p "$DB_PORT" -U "$FZL_POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 -qtA "$@"; }
psql_orbeon() { psql -h "$DB_HOST" -p "$DB_PORT" -U "$FZL_POSTGRES_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -qtA "$@"; }

echo "[fzl-orbeon] waiting for postgres at ${DB_HOST}:${DB_PORT} ..."
for _ in $(seq 1 60); do
    if psql_admin -c 'SELECT 1' >/dev/null 2>&1; then break; fi
    sleep 2
done

# 2a. Create the database if it doesn't exist (CREATE DATABASE can't be IF NOT EXISTS).
if [ "$(psql_admin -c "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'")" != "1" ]; then
    echo "[fzl-orbeon] creating database '${DB_NAME}' ..."
    psql_admin -c "CREATE DATABASE \"${DB_NAME}\""
fi

# 2b. Apply the Orbeon schema once (detected via a known Orbeon table).
if [ "$(psql_orbeon -c "SELECT to_regclass('public.orbeon_form_definition') IS NOT NULL")" != "t" ]; then
    echo "[fzl-orbeon] applying Orbeon 2025.1 schema ..."
    psql_orbeon -f /opt/fzl/postgresql-2025_1.sql
else
    echo "[fzl-orbeon] Orbeon schema already present — skipping."
fi

# 3. Render config from templates. envsubst is given an explicit variable list
#    so it does NOT touch Tomcat's own ${catalina.base} placeholders.
export ORBEON_DB_HOST="$DB_HOST" ORBEON_DB_PORT="$DB_PORT" ORBEON_DB_NAME="$DB_NAME"
export ORBEON_DB_USER="$FZL_POSTGRES_USER" ORBEON_DB_PASSWORD="$FZL_POSTGRES_PASSWORD"
export ORBEON_CRYPTO_PASSWORD="$FZL_ORBEON_CRYPTO_PASSWORD"

envsubst '${ORBEON_DB_HOST} ${ORBEON_DB_PORT} ${ORBEON_DB_NAME} ${ORBEON_DB_USER} ${ORBEON_DB_PASSWORD}' \
    < /opt/fzl/context.xml.template > "${CATALINA_HOME}/conf/context.xml"
envsubst '${ORBEON_CRYPTO_PASSWORD}' \
    < /opt/fzl/properties-local.xml.template \
    > "${CATALINA_HOME}/webapps/orbeon/WEB-INF/resources/config/properties-local.xml"

echo "[fzl-orbeon] configuration ready — starting Tomcat."
exec "$@"
