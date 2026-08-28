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
#   containers/fzl-flowable-ui/certs/mkcert-ca.pem
#     -> imported into a writable copy of the JVM truststore at container
#        start (docker-compose.yml entrypoint), so Flowable's OIDC client
#        can do the same.
#   containers/fzl-oauth2-proxy/certs/mkcert-ca.crt
#     -> oauth2-proxy runs OIDC discovery against https://fzlbpms.local/auth/
#        on startup; without this CA that call fails TLS verification and
#        the proxy never becomes ready, so /theia/ returns 500 for everyone.
#   containers/fzl-theia/certs/mkcert-ca.crt
#     -> lets terminals inside the IDE curl https://fzlbpms.local without -k.
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

CAROOT="$(mkcert -CAROOT)"

if [ ! -f "$CAROOT/rootCA.pem" ]; then
    log "Creating mkcert local root CA..."
    mkcert -install </dev/null 2>/dev/null || true
fi

# mkcert automatically covers ~/.mozilla/firefox/ and default NSS stores,
# but Firefox forks (Zen Browser at ~/.config/zen/) and Chromium forks (Brave)
# benefit from direct registration in their cert databases:
if command -v certutil >/dev/null 2>&1; then
    # Ensure Chromium / Brave default NSS DB has the CA
    mkdir -p "$HOME/.pki/nssdb"
    if [ ! -f "$HOME/.pki/nssdb/cert9.db" ]; then
        certutil -d sql:"$HOME/.pki/nssdb" -N --empty-password -f <(echo "") 2>/dev/null || true
    fi
    certutil -d sql:"$HOME/.pki/nssdb" -A -t "C,," -n "mkcert development CA" -i "$CAROOT/rootCA.pem" -f <(echo "") 2>/dev/null || true

    # Register in all Zen Browser profiles
    for zpath in "$HOME/.config/zen" "$HOME/.zen"; do
        if [ -d "$zpath" ]; then
            for prof in "$zpath"/*/; do
                if [ -f "${prof}cert9.db" ] || [ -f "${prof}cert8.db" ]; then
                    log "Trusting mkcert CA in Zen profile: $(basename "$prof")"
                    certutil -d sql:"$prof" -A -t "C,," -n "mkcert development CA" -i "$CAROOT/rootCA.pem" -f <(echo "") 2>/dev/null || true
                fi
            done
        fi
    done
fi

NGINX_CERTS_DIR="containers/fzl-nginx/certs"
PHP_CERTS_DIR="containers/fzl-php8.3-fpm/certs"
FLOWABLE_CERTS_DIR="containers/fzl-flowable-ui/certs"
KARAF_CERTS_DIR="containers/fzl-karaf-camel-integration/certs"
OAUTH2_CERTS_DIR="containers/fzl-oauth2-proxy/certs"
THEIA_CERTS_DIR="containers/fzl-theia/certs"
mkdir -p "$NGINX_CERTS_DIR" "$PHP_CERTS_DIR" "$FLOWABLE_CERTS_DIR" "$KARAF_CERTS_DIR" \
         "$OAUTH2_CERTS_DIR" "$THEIA_CERTS_DIR"

log "Issuing a certificate for fzlbpms.local..."
mkcert \
    -cert-file "$NGINX_CERTS_DIR/fzlbpms.local.pem" \
    -key-file "$NGINX_CERTS_DIR/fzlbpms.local-key.pem" \
    fzlbpms.local

CAROOT="$(mkcert -CAROOT)"
log "Copying mkcert's root CA (${CAROOT}/rootCA.pem) for Moodle/Flowable to trust..."
cp "$CAROOT/rootCA.pem" "$PHP_CERTS_DIR/mkcert-ca.crt"
cp "$CAROOT/rootCA.pem" "$FLOWABLE_CERTS_DIR/mkcert-ca.pem"
cp "$CAROOT/rootCA.pem" "$KARAF_CERTS_DIR/mkcert-ca.pem"
cp "$CAROOT/rootCA.pem" "$OAUTH2_CERTS_DIR/mkcert-ca.crt"
cp "$CAROOT/rootCA.pem" "$THEIA_CERTS_DIR/mkcert-ca.crt"

log "Done. Restart the affected containers to pick this up:"
log "  docker compose up -d fzl-nginx fzl-php8.3-fpm fzl-flowable-ui fzl-oauth2-proxy fzl-theia"
log "  docker compose up moodle-oauth2-configurator"
log "Then add this to /etc/hosts if you haven't already:"
log "  127.0.0.1 fzlbpms.local"
log "(ansible/fzlbpms-setup.yml does the /etc/hosts entry for you.)"
