#!/bin/sh
# Runs automatically before nginx starts (official nginx image convention:
# every executable script in /docker-entrypoint.d/ runs, sorted by name).
#
# /run/mkcert-certs is a directory bind mount of containers/fzl-nginx/certs
# on the host (see docker-compose.yml) — populated by
# ansible/setup-project.yml (--tags certs). Mounting a directory is safe even when it
# doesn't exist yet on the host (Docker just creates it empty), unlike
# mounting a single file path, so `docker compose up` never fails here for
# someone who hasn't run the mkcert setup.
#
# /etc/nginx/certs (NOT bind-mounted) already has a self-signed fallback
# baked in at image build time (see Dockerfile) so nginx always has a valid
# cert to load; this script overwrites it with the real mkcert-issued one
# when available.
set -eu

SRC=/run/mkcert-certs
DEST=/etc/nginx/certs

if [ -f "$SRC/fzlbpms.local.pem" ] && [ -f "$SRC/fzlbpms.local-key.pem" ]; then
    cp "$SRC/fzlbpms.local.pem" "$DEST/fzlbpms.local.pem"
    cp "$SRC/fzlbpms.local-key.pem" "$DEST/fzlbpms.local-key.pem"
    echo "$0: installed the mkcert-issued certificate for fzlbpms.local."
else
    echo "$0: no mkcert certificate at $SRC — https://fzlbpms.local will use the built-in self-signed fallback (browser will warn). Run: ansible-playbook -i ansible/inventory.ini ansible/setup-project.yml --tags certs -K"
fi
