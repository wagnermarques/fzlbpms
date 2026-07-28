#!/usr/bin/env bash
# =============================================================================
# setup-local-https.sh — issues a browser-trusted TLS certificate for
# https://fzlbpms.local using mkcert, and stages it (plus mkcert's root CA)
# where docker-compose.yml expects to find them:
#
#   containers/fzl-nginx/certs/fzlbpms.local.pem + -key.pem
#     -> nginx's TLS listener (containers/fzl-nginx/docker-entrypoint.d/
#        10-install-mkcert-certs.sh installs it over the built-in
#        self-signed fallback at container start).
#   containers/fzl-php8.3-fpm/certs/mkcert-ca.crt
#     -> trusted by fzl-php8.3-fpm/moodle-oauth2-configurator at container
#        start (docker-entrypoint.sh: update-ca-certificates), so Moodle's
#        PHP backend can call https://fzlbpms.local directly.
#   containers/flowable-ui/certs/mkcert-ca.pem
#     -> imported into a writable copy of the JVM truststore at container
#        start (docker-compose.yml entrypoint), so Flowable's OIDC client
#        can do the same.
#
# All idempotent — safe to re-run any time (e.g. after mkcert's CA rotates).
# Called by ansible/fzlbpms-setup.yml; you can also run it directly.
#
# Requires: mkcert (dnf install mkcert nss-tools on Fedora). If missing,
# this script prints instructions and exits 0 without touching anything —
# nginx still starts fine using its built-in self-signed fallback, just
# with a browser trust warning, and Moodle/Flowable's server-side calls to
# https://fzlbpms.local will fail TLS verification until this is run.
# =============================================================================
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

log() { echo "[setup-local-https] $*"; }

if ! command -v mkcert >/dev/null 2>&1; then
    log "mkcert not found — skipping. Install it and re-run:"
    log "  sudo dnf install mkcert nss-tools   # Fedora"
    log "Or run: ansible-playbook ansible/fzlbpms-setup.yml -i ansible/inventory.ini -K"
    exit 0
fi

log "Ensuring mkcert's local CA is installed and trusted (mkcert -install)..."
# system,nss only: the containers get the CA via their own bind mounts (see
# header), so touching host JVM keystores (which can require extra
# privileges) is pointless here. When ansible/fzlbpms-setup.yml has already
# pre-trusted the CA in both stores, this is a no-op that never asks for
# sudo — which is what lets it run non-interactively under Ansible.
TRUST_STORES=system,nss mkcert -install

NGINX_CERTS_DIR="containers/fzl-nginx/certs"
PHP_CERTS_DIR="containers/fzl-php8.3-fpm/certs"
FLOWABLE_CERTS_DIR="containers/flowable-ui/certs"
mkdir -p "$NGINX_CERTS_DIR" "$PHP_CERTS_DIR" "$FLOWABLE_CERTS_DIR"

log "Issuing a certificate for fzlbpms.local..."
mkcert \
    -cert-file "$NGINX_CERTS_DIR/fzlbpms.local.pem" \
    -key-file "$NGINX_CERTS_DIR/fzlbpms.local-key.pem" \
    fzlbpms.local

CAROOT="$(mkcert -CAROOT)"
log "Copying mkcert's root CA (${CAROOT}/rootCA.pem) for Moodle/Flowable to trust..."
cp "$CAROOT/rootCA.pem" "$PHP_CERTS_DIR/mkcert-ca.crt"
cp "$CAROOT/rootCA.pem" "$FLOWABLE_CERTS_DIR/mkcert-ca.pem"

log "Done. Restart the affected containers to pick this up:"
log "  docker compose up -d fzl-nginx fzl-php8.3-fpm flowable-ui"
log "  docker compose up moodle-oauth2-configurator"
log "Then add this to /etc/hosts if you haven't already:"
log "  127.0.0.1 fzlbpms.local"
log "(ansible/fzlbpms-setup.yml does the /etc/hosts entry for you.)"
