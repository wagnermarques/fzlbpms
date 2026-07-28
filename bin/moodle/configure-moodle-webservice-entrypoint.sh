#!/usr/bin/env bash
# =============================================================================
# configure-moodle-webservice-entrypoint.sh
#
# Retry wrapper for configure-moodle-webservice.php. Runs as the one-shot
# `moodle-webservice-configurator` compose service. Moodle
# (moodle-installer) may still be finishing when this container starts, so
# retry with backoff instead of failing hard.
# =============================================================================
set -uo pipefail

SCRIPT="/opt/moodle-scripts/configure-moodle-webservice.php"
WEB_USER="${WEB_USER:-www-data}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-20}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"

log() { echo "[ws-setup-entrypoint] $*"; }

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    if su -s /bin/bash -c "php '${SCRIPT}'" "$WEB_USER"; then
        log "Succeeded on attempt ${attempt}/${MAX_ATTEMPTS}."
        exit 0
    fi
    log "Attempt ${attempt}/${MAX_ATTEMPTS} failed — retrying in ${SLEEP_SECONDS}s..."
    sleep "$SLEEP_SECONDS"
done

log "ERROR — giving up after ${MAX_ATTEMPTS} attempts. Re-run: docker compose up moodle-webservice-configurator"
exit 1
