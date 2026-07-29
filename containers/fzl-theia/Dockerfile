# Eclipse Theia, browser target — the IDE fzl-nginx serves at /theia/.
#
# Debian, not Alpine, on purpose: Theia pulls native modules (node-pty, nsfw,
# drivelist) that are glibc-linked. Building them against musl is a known time
# sink for no benefit here.

# --- Build stage: install deps, bundle the frontend, fetch the VS Code plugins ---
FROM node:22-bookworm AS build

# python3/make/g++ build node-pty & friends from source; libsecret is needed by
# keytar, which @theia/core pulls in for credential storage.
RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 make g++ libsecret-1-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# yarn.lock* (glob) so the very first build works before a lockfile has been
# generated and committed. Once containers/fzl-theia/yarn.lock exists it is
# picked up and --frozen-lockfile below makes the build reproducible.
COPY package.json yarn.lock* ./
RUN if [ -f yarn.lock ]; then \
        yarn install --frozen-lockfile --network-timeout 600000; \
    else \
        echo "[fzl-theia] No yarn.lock yet — resolving fresh. Commit the generated" \
             "containers/fzl-theia/yarn.lock to make this build reproducible." && \
        yarn install --network-timeout 600000; \
    fi

COPY . .

# The production webpack pass OOMs on Node's default heap — this is not optional.
ENV NODE_OPTIONS=--max-old-space-size=4096
RUN yarn build && yarn download:plugins

# --- Runtime stage ---
FROM node:22-bookworm-slim

# git + a real shell make the built-in terminal and SCM view actually useful;
# libsecret-1-0 is the runtime half of the keytar dependency above.
RUN apt-get update && apt-get install -y --no-install-recommends \
        git openssh-client ca-certificates bash procps libsecret-1-0 \
    && rm -rf /var/lib/apt/lists/*

# Non-root. A browser IDE is a remote shell; it should not also be root.
#
# The uid MUST match the host user that owns ./workspaces, or the IDE can
# only write into world-writable directories and every file it creates lands
# on the host owned by a stranger. Default 1000 = the first regular user on
# most Linux hosts; override if yours differs:
#   docker compose build --build-arg THEIA_UID=$(id -u) \
#                        --build-arg THEIA_GID=$(id -g) fzl-theia
ARG THEIA_UID=1000
ARG THEIA_GID=1000

# node:22 already ships a uid/gid 1000 user called "node"; drop it so the
# requested uid is free whatever value it takes.
#
# /home/theia/.theia must exist AND be owned by the runtime user here: docker
# seeds a named volume from the image directory it is mounted over, inheriting
# its ownership. If the directory is missing at image build time the volume is
# created root-owned instead, and the backend dies with
# "EACCES: permission denied, mkdir '/home/theia/.theia/localization-cache'".
RUN userdel -r node 2>/dev/null || true; \
    groupadd -g ${THEIA_GID} theia 2>/dev/null || true; \
    useradd -m -u ${THEIA_UID} -g ${THEIA_GID} theia; \
    mkdir -p /home/workspace /home/theia/.theia /etc/fzlbpms; \
    chown -R ${THEIA_UID}:${THEIA_GID} /home/workspace /home/theia

COPY --from=build --chown=${THEIA_UID}:${THEIA_GID} /app /app

USER theia
WORKDIR /app

ENV THEIA_DEFAULT_PLUGINS=local-dir:/app/plugins \
    SHELL=/bin/bash \
    USE_LOCAL_GIT=true \
    NODE_EXTRA_CA_CERTS=/etc/fzlbpms/mkcert-ca.crt

# Webview-backed editors (the BPMN modeler) are served from a separate origin
# by default: {{uuid}}.webview.{{hostname}}. That needs wildcard DNS and a
# wildcard certificate, neither of which exists for fzlbpms.local — so the
# webview would simply fail to load behind nginx. "{{hostname}}" flips Theia
# to serveSameOrigin (see plugin-service.ts), serving webview assets from
# /theia/webview/... through the existing proxy block.
#
# Trade-off: webview content no longer sits in its own origin sandbox. That is
# acceptable here because every plugin is baked in at build time and the IDE is
# already restricted to holders of the theia-user role.
ENV THEIA_WEBVIEW_EXTERNAL_ENDPOINT="{{hostname}}"

EXPOSE 3000

# Run the generated backend directly so @theia/cli (a devDependency) isn't
# needed at runtime. The frontend is served from /app/lib/frontend by this
# same process; nginx strips the /theia prefix before it arrives here.
CMD ["node", "/app/src-gen/backend/main.js", "/home/workspace", \
     "--hostname=0.0.0.0", "--port=3000"]
