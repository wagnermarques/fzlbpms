# Eclipse Theia, browser target — the IDE fzl-nginx serves at /theia/.
#
# Debian, not Alpine, on purpose: Theia pulls native modules (node-pty, nsfw,
# drivelist) that are glibc-linked. Building them against musl is a known time
# sink for no benefit here.

# --- Build stage: install deps, bundle the frontend, fetch the VS Code plugins ---
FROM node:22-bookworm AS build

# Retry apt fetches: a brief DNS/network hiccup on the host otherwise kills the
# whole (long) build. See ./bin/docker-dns-preflight.sh for the DNS side of it.
RUN printf 'Acquire::Retries "5";\nAcquire::http::Timeout "20";\nAcquire::https::Timeout "20";\n' \
    > /etc/apt/apt.conf.d/99fzl-retries

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

RUN printf 'Acquire::Retries "5";\nAcquire::http::Timeout "20";\nAcquire::https::Timeout "20";\n' \
    > /etc/apt/apt.conf.d/99fzl-retries

# git + a real shell make the built-in terminal and SCM view actually useful;
# libsecret-1-0 is the runtime half of the keytar dependency above.
RUN apt-get update && apt-get install -y --no-install-recommends \
        git openssh-client ca-certificates bash procps libsecret-1-0 \
    && rm -rf /var/lib/apt/lists/*

# Eclipse Theia's own source, vendored for in-IDE reference/browsing while
# writing plugins (see docker-entrypoint.sh for how it's exposed in the
# workspace) — NOT what builds this app; that still comes from the published
# @theia/* packages above. Pinned to the same version. Shallow (no history)
# and world-readable rather than owned by the theia user, since it's
# reference-only and never written to.
ARG THEIA_REF_VERSION=v1.73.1
RUN git clone --depth 1 --branch ${THEIA_REF_VERSION} \
        https://github.com/eclipse-theia/theia.git /opt/theia-reference \
    && rm -rf /opt/theia-reference/.git \
    && chmod -R a+rX /opt/theia-reference

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

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

# Two local-dir sources: /app/plugins is baked in at image-build time
# (theiaPlugins in package.json, e.g. bpmn-modeler); /home/custom-plugins is
# bind-mounted from src-projects/fzl-theia-plugins-dist (see
# docker-compose.yml + bin/theia/build-plugins.sh) so custom plugins can be
# rebuilt and picked up with just a container restart, no image rebuild.
# THEIA_DEFAULT_PLUGINS is comma-split, each entry resolved independently by
# its own scheme prefix — confirmed in
# packages/plugin-ext/src/main/node/plugin-deployer-impl.ts upstream.
ENV THEIA_DEFAULT_PLUGINS=local-dir:/app/plugins,local-dir:/home/custom-plugins \
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

# Symlinks the vendored Theia reference source into the workspace on every
# start (see docker-entrypoint.sh), then execs CMD below.
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

# Run the generated backend directly so @theia/cli (a devDependency) isn't
# needed at runtime. The frontend is served from /app/lib/frontend by this
# same process; nginx strips the /theia prefix before it arrives here.
CMD ["node", "/app/src-gen/backend/main.js", "/home/workspace", \
     "--hostname=0.0.0.0", "--port=3000"]
