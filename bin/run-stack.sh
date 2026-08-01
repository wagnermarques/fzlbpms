#!/bin/bash
# Starts a named subset of the docker compose services, as defined in
# run-stack.toml at the project root. See that file for usage examples.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOML_FILE="$PROJECT_DIR/run-stack.toml"
ENV_FILE="$PROJECT_DIR/.env"
ENV_TEMPLATE="$PROJECT_DIR/.env.template"

if [ ! -f "$TOML_FILE" ]; then
    echo "ERROR: $TOML_FILE not found" >&2
    exit 1
fi

# A fresh clone has no .env — it is gitignored. Without it every compose command
# dies with "required variable FZL_NGINX_PORT_HTTP is missing a value", so seed
# it from the template instead of making each new clone fail first.
if [ ! -f "$ENV_FILE" ]; then
    if [ ! -f "$ENV_TEMPLATE" ]; then
        echo "ERROR: neither .env nor .env.template found in $PROJECT_DIR" >&2
        exit 1
    fi
    cp "$ENV_TEMPLATE" "$ENV_FILE"
    echo "NOTE: .env did not exist — created it from .env.template."
    echo "      Review domains, ports and secrets in $ENV_FILE before"
    echo "      exposing this stack outside your machine."
fi

MODE="up"
DRY_RUN=0
STACKS=()

for arg in "$@"; do
    case "$arg" in
        --list|-l)    MODE="list" ;;
        --down|-d)    MODE="down" ;;
        --dry-run|-n) DRY_RUN=1 ;;
        --help|-h)
            sed -n '2,15p' "$TOML_FILE" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        -*)
            echo "ERROR: unknown option: $arg (see --help)" >&2
            exit 1 ;;
        *)  STACKS+=("$arg") ;;
    esac
done

# Resolve stacks -> service list (python3 stdlib only; tomllib needs python >= 3.11)
resolve() {
    python3 - "$TOML_FILE" "$@" <<'PYEOF'
import sys, tomllib

toml_path, *wanted = sys.argv[1:]
with open(toml_path, "rb") as f:
    cfg = tomllib.load(f)
stacks = cfg.get("stacks", {})

if wanted == ["--list--"]:
    width = max(map(len, stacks), default=0)
    for name, st in stacks.items():
        print(f"{name:<{width}}  {st.get('description', '')}")
        deps = f" (+ {', '.join(st['includes'])})" if st.get("includes") else ""
        print(f"{'':<{width}}    -> {', '.join(st.get('services', [])) or '-'}{deps}")
    sys.exit(0)

if not wanted:
    if "default" not in cfg:
        sys.exit("ERROR: no stack given and no 'default' in run-stack.toml")
    wanted = [cfg["default"]]

services, seen = [], set()
def expand(name, trail):
    if name in trail:
        sys.exit(f"ERROR: circular 'includes' involving stack '{name}'")
    if name not in stacks:
        sys.exit(f"ERROR: unknown stack '{name}'. Available: {', '.join(stacks)}")
    for inc in stacks[name].get("includes", []):
        expand(inc, trail | {name})
    for svc in stacks[name].get("services", []):
        if svc not in seen:
            seen.add(svc)
            services.append(svc)

for name in wanted:
    expand(name, set())
print("\n".join(services))
PYEOF
}

if [ "$MODE" = "list" ]; then
    resolve "--list--"
    exit 0
fi

mapfile -t SERVICES < <(resolve "${STACKS[@]+"${STACKS[@]}"}")

# fzl-cloudflared (public Cloudflare Tunnel for fzlbpms.com.br) rides along
# with every stack, not just the ones that list it explicitly in
# run-stack.toml — it has to be up whenever fzl-nginx is, and down when the
# stack it's proxying goes down.
if ! printf '%s\n' "${SERVICES[@]}" | grep -qx "fzl-cloudflared"; then
    SERVICES+=("fzl-cloudflared")
fi

# Validate service names against docker-compose.yml before doing anything.
# 'docker compose config' also fails when .env is incomplete; capture its status
# so that surfaces as the real error instead of the misleading
# "service 'x' in run-stack.toml does not exist in docker-compose.yml".
if ! COMPOSE_OUTPUT="$(cd "$PROJECT_DIR" && docker compose config --services 2>&1)"; then
    echo "ERROR: 'docker compose config' failed — fix docker-compose.yml or .env first:" >&2
    echo "$COMPOSE_OUTPUT" >&2
    exit 1
fi
mapfile -t KNOWN <<< "$COMPOSE_OUTPUT"
for svc in "${SERVICES[@]}"; do
    if ! printf '%s\n' "${KNOWN[@]}" | grep -qx "$svc"; then
        echo "ERROR: service '$svc' in run-stack.toml does not exist in docker-compose.yml" >&2
        exit 1
    fi
done

if [ "$MODE" = "down" ]; then
    CMD=(docker compose stop "${SERVICES[@]}")
else
    # Images are built on a fresh clone, and every 'apt-get update' in those
    # builds uses the nameservers Docker copies from the host — with no
    # failover. Catch a dead first resolver here instead of after a 3-minute
    # build that ends in "Temporary failure resolving 'deb.debian.org'".
    if [ "$DRY_RUN" != "1" ] && [ -x "$PROJECT_DIR/bin/docker-dns-preflight.sh" ]; then
        "$PROJECT_DIR/bin/docker-dns-preflight.sh" || true
    fi
    CMD=(docker compose up -d "${SERVICES[@]}")
fi

echo " ==> ${CMD[*]}"
if [ "$DRY_RUN" = "1" ]; then
    exit 0
fi
cd "$PROJECT_DIR" && "${CMD[@]}"
