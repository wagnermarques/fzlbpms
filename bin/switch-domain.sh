#!/usr/bin/env bash
# =============================================================================
# switch-domain.sh — switch the running fzlbpms stack between "localhost"
# and a public domain (e.g. fzlbpms.com.br).
#
# fzlbpmsadmin-web itself is domain-agnostic (the SPA derives its Keycloak
# issuer from window.location.origin). What can't be dynamic:
#
#   - Keycloak's own KC_HOSTNAME must be a single fixed value: every URL it
#     generates (login page, discovery document, token endpoint) needs to be
#     consistent for both browser-redirect flows AND direct container-to-
#     container calls (Moodle/Flowable call Keycloak's endpoints directly,
#     with no browser Host header to key off of) — see the KC_HOSTNAME
#     comment in docker-compose.yml.
#   - Moodle's $CFG->wwwroot and its OAuth2 issuer baseurl are single fixed
#     values by Moodle's own architecture (config.php, not per-request).
#   - Flowable UI's OIDC issuer URI is read once at JVM boot.
#
# This script updates .env, patches Moodle's already-installed config.php,
# and restarts/re-runs exactly the services that need it. Safe to re-run.
#
# Usage: bin/switch-domain.sh localhost
#        bin/switch-domain.sh fzlbpms.com.br
# =============================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

log() { echo "[switch-domain] $*"; }

DOMAIN="${1:?Usage: $0 <domain>  (e.g. localhost or fzlbpms.com.br)}"
ENV_FILE=".env"
CONFIG_PHP="src-projects/var_www/html/moodle/config.php"

if [ ! -f "$ENV_FILE" ]; then
    echo "[switch-domain] ERROR — ${ENV_FILE} not found. Copy .env.template to .env first." >&2
    exit 1
fi

if [ "$DOMAIN" = "localhost" ]; then
    PROTO="http"
else
    PROTO="https"
fi
NEW_WWWROOT="${PROTO}://${DOMAIN}/moodle"

# oauth2-proxy (the gate in front of /theia/) must not set a Secure cookie on
# a plain-http stack — the browser would refuse to send it back and the login
# would loop forever between Keycloak and /oauth2/start.
if [ "$PROTO" = "https" ]; then
    COOKIE_SECURE="true"
else
    COOKIE_SECURE="false"
fi

log "Domain: ${DOMAIN}  (proto: ${PROTO})"

log "Updating FZL_PUBLIC_HOSTNAME / FZL_PUBLIC_PROTO / MOODLE_WWWROOT / FZL_OAUTH2_COOKIE_SECURE in ${ENV_FILE}..."
sed -i \
    -e "s#^FZL_PUBLIC_HOSTNAME=.*#FZL_PUBLIC_HOSTNAME=${DOMAIN}#" \
    -e "s#^FZL_PUBLIC_PROTO=.*#FZL_PUBLIC_PROTO=${PROTO}#" \
    -e "s#^MOODLE_WWWROOT=.*#MOODLE_WWWROOT=${NEW_WWWROOT}#" \
    -e "s#^FZL_OAUTH2_COOKIE_SECURE=.*#FZL_OAUTH2_COOKIE_SECURE=${COOKIE_SECURE}#" \
    "$ENV_FILE"

if [ -f "$CONFIG_PHP" ]; then
    # config.php is owned by www-data (uid 33, written by moodle-installer) —
    # the host user can't write it directly, so edit it from inside
    # fzl-php8.3-fpm, which shares the bind mount and runs as root.
    log "Patching Moodle's installed config.php wwwroot -> ${NEW_WWWROOT}..."
    docker compose exec -T fzl-php8.3-fpm sed -i \
        "s#^\(\\\$CFG->wwwroot *= *\)'.*';#\1'${NEW_WWWROOT}';#" \
        /var/www/html/moodle/config.php \
        || log "WARNING — couldn't patch config.php (is fzl-php8.3-fpm running?). Run this script again once it's up."
else
    log "Moodle not installed yet (no ${CONFIG_PHP}) — wwwroot will be set correctly on first install."
fi

log "Restarting fzl-keycloak (picks up the new KC_HOSTNAME)..."
docker compose up -d fzl-keycloak

# --- Re-register the SSO clients' redirect URIs for the new domain ----------
# The Camel bootstrap (keycloak-admin-camel-context.xml) only CREATES these
# clients when missing — it never updates an existing one, so after a domain
# switch Keycloak still holds the old hostname's redirect URIs and every
# login dies with "Invalid parameter: redirect_uri". Fix them directly
# through Keycloak's admin REST API. localhost entries are always kept so a
# LAN/dev session keeps working alongside the public domain.

env_get() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-; }

KC_PORT="$(env_get FZL_KEYCLOAK_PORT)"
KC_ADMIN_USER="$(env_get FZL_KEYCLOAK_ADMIN_USER)"
KC_ADMIN_PASS="$(env_get FZL_KEYCLOAK_ADMIN_PASSWORD)"
WEBAPP_CLIENT="$(env_get FZL_WEBAPP_CLIENT_ID)"
MOODLE_CLIENT="$(env_get FZL_MOODLE_CLIENT_ID)"
FLOWABLE_CLIENT="$(env_get FZL_FLOWABLE_CLIENT_ID)"
THEIA_CLIENT="$(env_get FZL_THEIA_CLIENT_ID)"
KC_BASE="http://localhost:${KC_PORT}/auth"

log "Waiting for Keycloak on ${KC_BASE}..."
KC_READY=""
for _ in $(seq 1 60); do
    if curl -sf "${KC_BASE}/realms/master/.well-known/openid-configuration" -o /dev/null; then
        KC_READY=1
        break
    fi
    sleep 2
done

update_client() {
    # $1 = clientId, $2 = JSON array of redirectUris, $3 = JSON array of webOrigins ('' = leave as-is)
    local client_id="$1" redirects_json="$2" origins_json="$3" uuid
    uuid=$(curl -sf -H "Authorization: Bearer ${KC_TOKEN}" \
        "${KC_BASE}/admin/realms/fzlbpms/clients?clientId=${client_id}" \
        | python3 -c 'import sys,json;l=json.load(sys.stdin);print(l[0]["id"] if l else "")')
    if [ -z "$uuid" ]; then
        log "WARNING — Keycloak client '${client_id}' not found (Camel bootstrap not run yet?); skipping."
        return 0
    fi
    curl -sf -H "Authorization: Bearer ${KC_TOKEN}" "${KC_BASE}/admin/realms/fzlbpms/clients/${uuid}" \
        | REDIRECTS="$redirects_json" ORIGINS="$origins_json" python3 -c '
import json, os, sys
rep = json.load(sys.stdin)
rep["redirectUris"] = json.loads(os.environ["REDIRECTS"])
if os.environ["ORIGINS"]:
    rep["webOrigins"] = json.loads(os.environ["ORIGINS"])
print(json.dumps(rep))' \
        | curl -sf -X PUT -H "Authorization: Bearer ${KC_TOKEN}" \
            -H "Content-Type: application/json" -d @- \
            "${KC_BASE}/admin/realms/fzlbpms/clients/${uuid}"
    log "Keycloak client '${client_id}' redirect URIs now include ${PROTO}://${DOMAIN}."
}

if [ -n "$KC_READY" ]; then
    KC_TOKEN=$(curl -sf -X POST "${KC_BASE}/realms/master/protocol/openid-connect/token" \
        -d grant_type=password -d client_id=admin-cli \
        --data-urlencode "username=${KC_ADMIN_USER}" \
        --data-urlencode "password=${KC_ADMIN_PASS}" \
        | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))') || KC_TOKEN=""
    if [ -n "$KC_TOKEN" ]; then
        update_client "$MOODLE_CLIENT" \
            "[\"${PROTO}://${DOMAIN}/moodle/admin/oauth2callback.php\",\"http://localhost/moodle/admin/oauth2callback.php\"]" \
            ""
        update_client "$FLOWABLE_CLIENT" \
            "[\"${PROTO}://${DOMAIN}/flowable-ui/*\",\"http://localhost/flowable-ui/*\",\"http://localhost:8080/flowable-ui/*\"]" \
            ""
        update_client "$WEBAPP_CLIENT" \
            "[\"${PROTO}://${DOMAIN}/fzlbpmsadmin/*\",\"http://localhost/fzlbpmsadmin/*\",\"http://localhost:4200/*\"]" \
            "[\"${PROTO}://${DOMAIN}\",\"http://localhost\",\"http://localhost:4200\"]"
        # oauth2-proxy's callback, which gates the Theia IDE at /theia/.
        update_client "$THEIA_CLIENT" \
            "[\"${PROTO}://${DOMAIN}/oauth2/callback\",\"http://localhost/oauth2/callback\"]" \
            ""
    else
        log "WARNING — couldn't get a Keycloak admin token; redirect URIs NOT updated. Re-run this script once Keycloak is healthy."
    fi
else
    log "WARNING — Keycloak never became ready; redirect URIs NOT updated. Re-run this script once it's up."
fi

log "Restarting fzl-flowable-ui (picks up the new OIDC issuer URI)..."
docker compose up -d fzl-flowable-ui

# The issuer URL, redirect URL and cookie-secure flag are all baked into this
# container's command line from .env, so it has to be recreated, not just
# restarted. Ignored if the ide stack isn't running.
log "Recreating fzl-oauth2-proxy (picks up the new issuer / cookie settings)..."
docker compose up -d fzl-oauth2-proxy 2>/dev/null \
    || log "  (fzl-oauth2-proxy not running — skipped.)"

log "Re-running the Moodle OAuth2 issuer configurator against the new domain..."
docker compose up moodle-oauth2-configurator

log "Done. fzlbpmsadmin-web needs no rebuild — reload the page at:"
log "  ${PROTO}://${DOMAIN}/fzlbpmsadmin"
log "  ${PROTO}://${DOMAIN}/moodle"
