#!/bin/sh
# Runs before nginx starts (official nginx image convention: every executable
# script in /docker-entrypoint.d/ runs, sorted by name).
#
# Generates the Authorization header nginx injects towards Karaf on the
# /system/console and /hawtio routes.
#
# Why an injected header at all: those two tools are reached ONLY through this
# proxy (Karaf publishes 8181 on the Docker network, never on the host), and
# in front of them nginx already requires a Keycloak session via
# auth_request/oauth2-proxy. Karaf's own HTTP Basic realm stays enabled behind
# that — dropping it would leave the console reachable, unauthenticated, by
# anything else on fzl-network (a terminal inside the Theia IDE, for one).
# So the human authenticates with Keycloak, and nginx presents Karaf's service
# credential on their behalf. Users never see or type it.
#
# The credential comes from the environment (docker-compose.yml passes
# FZL_KARAF_USER / FZL_KARAF_PASSWORD from .env, which is gitignored) so it is
# not baked into this image or committed. It must match a user in
# containers/fzl-karaf-camel-integration/custom_karaf_etc/etc-from-4.4.7/users.properties.
set -eu

DEST=/etc/nginx/shared/karaf-auth.conf
USER="${FZL_KARAF_USER:-}"
PASS="${FZL_KARAF_PASSWORD:-}"

if [ -n "$USER" ] && [ -n "$PASS" ]; then
    ENCODED=$(printf '%s:%s' "$USER" "$PASS" | base64 | tr -d '\n')
    printf 'proxy_set_header Authorization "Basic %s";\n' "$ENCODED" > "$DEST"
    echo "$0: Karaf service credential installed for user '$USER'."
else
    # Empty (but present) so the `include` in app-server.conf still resolves:
    # a missing file is a fatal nginx config error. Karaf then falls back to
    # prompting for HTTP Basic itself, on top of the Keycloak sign-in.
    : > "$DEST"
    echo "$0: FZL_KARAF_USER/FZL_KARAF_PASSWORD unset — /system/console and /hawtio will ask for Karaf's own login in addition to Keycloak."
fi
