#!/usr/bin/env bash
# =============================================================================
# tasktoday-keycloak-setup.sh — Provision ONLY the Task Today pieces in the
# running Keycloak (realm fzlbpms), without re-applying the whole playbook:
#
#   - public PKCE client  fzl-tasktodayapp   (created if missing)
#   - realm role          tasktoday-admin    (created if missing)
#   - that role granted to FZLBPMS_ADMIN_USERNAME
#
# Idempotent: safe to run again. Same definitions as the "# 7." client and the
# tasktoday-admin role in ansible/keycloak-playbook--realms-and-clients-creation.yml.
#
# Usage (from the repo root, on the host running fzl-keycloak):
#   ./bin/tasktoday-keycloak-setup.sh
# Reads FZL_KEYCLOAK_ADMIN_USER/PASSWORD, FZLBPMS_ADMIN_USERNAME and
# FZL_KEYCLOAK_PORT (default 8083) from ./.env. Needs curl and jq.
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

env_get() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | sed -E 's/^"(.*)"$/\1/'; }
KC_ADMIN_USER=$(env_get FZL_KEYCLOAK_ADMIN_USER)
KC_ADMIN_PASSWORD=$(env_get FZL_KEYCLOAK_ADMIN_PASSWORD)
STACK_ADMIN=$(env_get FZLBPMS_ADMIN_USERNAME)
KC="http://localhost:$(env_get FZL_KEYCLOAK_PORT || true)"
[ "$KC" = "http://localhost:" ] && KC="http://localhost:8083"
KC="$KC/auth"
R="$KC/admin/realms/fzlbpms"

log() { echo "[tasktoday-keycloak] $*"; }

TOKEN=$(curl -sf "$KC/realms/master/protocol/openid-connect/token" -d grant_type=password -d client_id=admin-cli \
	--data-urlencode "username=$KC_ADMIN_USER" --data-urlencode "password=$KC_ADMIN_PASSWORD" | jq -r .access_token)
[ -n "$TOKEN" ] && [ "$TOKEN" != null ] || { log "admin login to $KC failed"; exit 1; }
H="Authorization: Bearer $TOKEN"

# --- client ------------------------------------------------------------------
if [ "$(curl -sf "$R/clients?clientId=fzl-tasktodayapp" -H "$H" | jq length)" = 0 ]; then
	curl -sf -X POST "$R/clients" -H "$H" -H 'Content-Type: application/json' -d @- <<'JSON'
{
  "clientId": "fzl-tasktodayapp",
  "name": "FZL Task Today App (GitHub Pages)",
  "enabled": true,
  "publicClient": true,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "rootUrl": "https://wagnermarques.github.io/fzl-tasktodayapp",
  "baseUrl": "https://wagnermarques.github.io/fzl-tasktodayapp/",
  "redirectUris": [
    "https://wagnermarques.github.io/fzl-tasktodayapp/*",
    "http://localhost:3030/*",
    "http://localhost:4173/*",
    "http://localhost:*",
    "http://127.0.0.1:*"
  ],
  "webOrigins": [
    "https://wagnermarques.github.io",
    "http://localhost:3030",
    "http://localhost:4173",
    "http://localhost:*",
    "+"
  ],
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "https://wagnermarques.github.io/fzl-tasktodayapp/*##http://localhost:3030/*##http://localhost:4173/*##http://localhost:*##http://127.0.0.1:*"
  }
}
JSON
	log "client fzl-tasktodayapp created"
else
	log "client fzl-tasktodayapp already exists"
fi

# --- realm role --------------------------------------------------------------
if ! curl -sf -o /dev/null "$R/roles/tasktoday-admin" -H "$H"; then
	curl -sf -X POST "$R/roles" -H "$H" -H 'Content-Type: application/json' \
		-d '{"name":"tasktoday-admin","description":"Administra as categorias nativas (compartilhadas) do Task Today App"}'
	log "realm role tasktoday-admin created"
else
	log "realm role tasktoday-admin already exists"
fi

# --- grant to the stack admin ------------------------------------------------
USER_ID=$(curl -sf "$R/users?username=$(jq -rn --arg u "$STACK_ADMIN" '$u|@uri')&exact=true" -H "$H" | jq -r '.[0].id // empty')
[ -n "$USER_ID" ] || { log "user $STACK_ADMIN not found in realm fzlbpms"; exit 1; }
ROLE=$(curl -sf "$R/roles/tasktoday-admin" -H "$H" | jq -c '[{id, name}]')
curl -sf -X POST "$R/users/$USER_ID/role-mappings/realm" -H "$H" -H 'Content-Type: application/json' -d "$ROLE"
log "tasktoday-admin granted to $STACK_ADMIN"
