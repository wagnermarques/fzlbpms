#!/usr/bin/env bash
# =============================================================================
# configure-oauth2-keycloak-entrypoint.sh
#
# Retry wrapper for configure-oauth2-keycloak.php. Runs as the one-shot
# `moodle-oauth2-configurator` compose service. Both Moodle itself
# (moodle-installer) and the Keycloak 'moodle' client (Camel's
# kc-setup-startup, which has its own internal 40s delay) may not be ready
# the instant this container starts, so retry with backoff instead of
# failing hard.
# =============================================================================
set -uo pipefail

SCRIPT="/opt/moodle-scripts/configure-oauth2-keycloak.php"
WEB_USER="${WEB_USER:-www-data}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-20}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"

log() { echo "[oauth2-setup-entrypoint] $*"; }

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    if su -s /bin/bash -c "php '${SCRIPT}'" "$WEB_USER"; then
        log "Succeeded on attempt ${attempt}/${MAX_ATTEMPTS}."
        exit 0
    fi
    log "Attempt ${attempt}/${MAX_ATTEMPTS} failed — retrying in ${SLEEP_SECONDS}s..."
    sleep "$SLEEP_SECONDS"
done

log "ERROR — giving up after ${MAX_ATTEMPTS} attempts. Re-run: docker compose up moodle-oauth2-configurator"
exit 1
